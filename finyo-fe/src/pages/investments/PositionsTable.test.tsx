import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { PositionsTable } from './PositionsTable';
import { portfolioApi } from '@/api/portfolio';
import type { PortfolioPosition } from '@/api/portfolio';
import { positionDetailApi } from '@/api/positionDetail';
import { renderWithProviders } from '@/test/test-utils';
import { portfolioPosition, positionDetail } from '@/test/fixtures/portfolio';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    user: { profile: {} },
    accessToken: 'test-token',
    roles: ['user'],
    hasRole: () => true,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock('@/api/portfolio', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/portfolio')>();
  return {
    ...actual,
    portfolioApi: { deletePosition: vi.fn() },
  };
});

vi.mock('@/api/positionDetail', () => ({
  positionDetailApi: { updatePosition: vi.fn() },
}));

// Deliberately unordered input: grouping must impose ETF → STOCK order.
const positions = [
  portfolioPosition({
    id: 'p1',
    assetClass: 'STOCK',
    name: 'Nestlé SA',
    isin: 'CH0038863350',
    quantity: 10,
    purchasePrice: 90,
    currentPrice: 100,
    value: 1000,
    gainLoss: 100,
    returnPct: 11.1,
    allocationPct: 50,
  }),
  portfolioPosition({
    id: 'p2',
    assetClass: 'ETF',
    name: 'iShares Core SPI',
    isin: 'CH0237935652',
    value: 600,
    gainLoss: -33.33,
    returnPct: -4.8,
    allocationPct: 30,
  }),
  portfolioPosition({
    id: 'p3',
    assetClass: 'STOCK',
    name: 'Roche Holding AG',
    isin: 'CH0012032048',
    value: 400,
    gainLoss: 20,
    returnPct: 5.3,
    allocationPct: 20,
  }),
];

function renderTable(items: PortfolioPosition[] = positions) {
  return renderWithProviders(
    <Routes>
      <Route path="/investments" element={<PositionsTable positions={items} />} />
      <Route path="/investments/positions/:positionId" element={<div>detail-page</div>} />
    </Routes>,
    { route: '/investments' },
  );
}

describe('PositionsTable', () => {
  beforeEach(() => {
    vi.mocked(portfolioApi.deletePosition).mockResolvedValue(undefined);
    vi.mocked(positionDetailApi.updatePosition).mockReset();
    vi.mocked(positionDetailApi.updatePosition).mockResolvedValue(
      positionDetail({ positionId: 'p2' }),
    );
  });

  it('groups positions under asset-class subheaders in display order', () => {
    renderTable();

    const rows = screen.getAllByRole('row');
    const rowTexts = rows.map((row) => row.textContent ?? '');
    const etfHeaderIndex = rowTexts.findIndex((text) => text.includes('ETF'));
    const etfRowIndex = rowTexts.findIndex((text) => text.includes('iShares Core SPI'));
    const stockHeaderIndex = rowTexts.findIndex((text) => text.includes('Individual stocks'));
    const nestleIndex = rowTexts.findIndex((text) => text.includes('Nestlé SA'));

    // ETF group (header + row) comes before the stock group.
    expect(etfHeaderIndex).toBeGreaterThan(-1);
    expect(etfHeaderIndex).toBeLessThan(etfRowIndex);
    expect(etfRowIndex).toBeLessThan(stockHeaderIndex);
    expect(stockHeaderIndex).toBeLessThan(nestleIndex);
  });

  it('shows group total and share in the subheader and hides empty groups', () => {
    renderTable();

    const stockHeader = screen.getByText('Individual stocks').closest('tr');
    expect(stockHeader).not.toBeNull();
    // 1000 + 400 of 2000 total → CHF 1'400.00 and 70.0%
    expect(within(stockHeader as HTMLElement).getByText("CHF 1'400.00")).toBeInTheDocument();
    expect(within(stockHeader as HTMLElement).getByText('70.0%')).toBeInTheDocument();

    // No FUND/CRYPTO/BOND positions → no subheaders for them.
    expect(screen.queryByText('Investment funds')).not.toBeInTheDocument();
    expect(screen.queryByText('Cryptocurrencies')).not.toBeInTheDocument();
    expect(screen.queryByText('Fixed deposits & bonds')).not.toBeInTheDocument();
  });

  it('navigates to the position detail page when a row is clicked', async () => {
    const user = userEvent.setup();
    renderTable();

    await user.click(screen.getByText('Nestlé SA'));

    expect(screen.getByText('detail-page')).toBeInTheDocument();
  });

  it('shows the empty state when there are no positions', () => {
    renderTable([]);

    expect(
      screen.getByText('No positions yet — add your first security above'),
    ).toBeInTheDocument();
  });

  it('deletes a position after confirmation without navigating away', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    const { queryClient } = renderTable();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    // First remove button belongs to the ETF group (iShares).
    await user.click(screen.getAllByRole('button', { name: 'Remove position' })[0]);

    expect(globalThis.confirm).toHaveBeenCalledWith('Remove position "iShares Core SPI"?');
    await waitFor(() =>
      expect(portfolioApi.deletePosition).toHaveBeenCalledWith('test-token', 'p2'),
    );
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['portfolio'] }),
    );
    // The ✕ click must not bubble into the row navigation.
    expect(screen.queryByText('detail-page')).not.toBeInTheDocument();
  });

  it('opens the edit dialog from the row action and PATCHes the holding', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderTable();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    // First edit button belongs to the ETF group (iShares, p2).
    await user.click(screen.getAllByRole('button', { name: 'Edit position' })[0]);

    expect(await screen.findByRole('heading', { name: 'Edit position' })).toBeInTheDocument();
    // prefilled from the table row
    expect(screen.getByLabelText('Quantity')).toHaveValue(10);
    expect(screen.getByLabelText('Avg. Purchase Price')).toHaveValue(90);
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(positionDetailApi.updatePosition).toHaveBeenCalledWith('test-token', 'p2', {
        quantity: 10,
        purchasePrice: 90,
        purchaseDate: null,
      }),
    );
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['position', 'p2'] }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['portfolio'] });
    // The pencil click must not bubble into the row navigation.
    expect(screen.queryByText('detail-page')).not.toBeInTheDocument();
  });

  it('does not delete when the confirmation is declined', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderTable();

    await user.click(screen.getAllByRole('button', { name: 'Remove position' })[0]);

    expect(portfolioApi.deletePosition).not.toHaveBeenCalled();
  });

  describe('price provenance', () => {
    const PRICE_COLUMN_INDEX = 3;

    /** The row cell that carries the price and its provenance badge. */
    function priceCell(name: string): HTMLElement {
      const row = screen.getByText(name).closest('tr') as HTMLElement;
      return row.querySelectorAll('td')[PRICE_COLUMN_INDEX];
    }

    it('leaves a fresh market price unbadged but names its trading day', () => {
      renderTable([portfolioPosition({ id: 'p1', name: 'Nestlé SA' })]);

      const cell = priceCell('Nestlé SA');
      expect(within(cell).queryByText('Market')).not.toBeInTheDocument();
      expect(within(cell).queryByText('No price')).not.toBeInTheDocument();
      expect(
        within(cell).getByText('Price from 10 Jul 2026 · Market price, 15 min delayed'),
      ).toBeInTheDocument();
    });

    it('badges a stale market price with the day it belongs to', () => {
      renderTable([
        portfolioPosition({ id: 'p1', name: 'Nestlé SA', priceAsOf: '2026-07-03', stale: true }),
      ]);

      const cell = priceCell('Nestlé SA');
      expect(within(cell).getByText('Price from 03 Jul 2026')).toBeInTheDocument();
      expect(
        within(cell).getByText(/Older than expected — the market may have moved since/),
      ).toBeInTheDocument();
    });

    it('badges a hand-typed price as manual', () => {
      renderTable([portfolioPosition({ id: 'p1', name: 'Nestlé SA', priceSource: 'MANUAL' })]);

      const cell = priceCell('Nestlé SA');
      expect(within(cell).getByText('Manual')).toBeInTheDocument();
      expect(within(cell).getByText(/A price you entered manually/)).toBeInTheDocument();
    });

    it('badges a position without any price — it is shown at purchase cost', () => {
      renderTable([
        portfolioPosition({
          id: 'p1',
          name: 'Nestlé SA',
          priceSource: 'PURCHASE',
          priceAsOf: null,
          currency: null,
          gainLoss: 0,
          returnPct: 0,
        }),
      ]);

      const cell = priceCell('Nestlé SA');
      expect(within(cell).getByText('No price')).toBeInTheDocument();
      expect(
        within(cell).getByText(
          'No price available — the position is shown at its purchase cost',
        ),
      ).toBeInTheDocument();
    });

    it('explains the price on hover', async () => {
      const user = userEvent.setup();
      renderTable([portfolioPosition({ id: 'p1', name: 'Nestlé SA', priceSource: 'PURCHASE' })]);

      await user.hover(within(priceCell('Nestlé SA')).getByText('CHF 100.00'));

      const tooltip = await screen.findByRole('tooltip');
      expect(tooltip).toHaveTextContent(
        'No price available — the position is shown at its purchase cost',
      );
    });
  });
});
