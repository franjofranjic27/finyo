import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CompareTab } from './CompareTab';
import { pillar3Api } from '@/api/pillar3';
import type { Pillar3CompareRequest } from '@/api/pillar3';
import {
  pillar3CompareResponse,
  pillar3Comparison,
  pillar3Product,
} from '@/test/fixtures/pillar3';
import { renderWithProviders } from '@/test/test-utils';

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

vi.mock('@/api/pillar3', () => ({
  pillar3Api: {
    getProducts: vi.fn(),
    compare: vi.fn(),
  },
}));

const catalog = [
  pillar3Product({ id: 'a', name: 'Alpha Fund', isin: 'CH0000000010' }),
  pillar3Product({ id: 'b', name: 'Beta Fund', isin: 'CH0000000021' }),
];

const comparisons = {
  a: pillar3Comparison({ productId: 'a', name: 'Alpha Fund', finalCapital: 180_000 }),
  b: pillar3Comparison({ productId: 'b', name: 'Beta Fund', finalCapital: 220_000 }),
};

/** Echoes the requested products, sorted by finalCapital desc like the backend. */
function mockCompareFromRequest() {
  vi.mocked(pillar3Api.compare).mockImplementation((_token, request: Pillar3CompareRequest) => {
    const products = request.productIds
      .map((id) => comparisons[id as keyof typeof comparisons])
      .sort((x, y) => y.finalCapital - x.finalCapital);
    return Promise.resolve(pillar3CompareResponse(products));
  });
}

async function selectProduct(user: ReturnType<typeof userEvent.setup>, name: string) {
  // Typing (re)opens the result list even when the input already has focus.
  await user.type(screen.getByRole('textbox', { name: 'Add product' }), name);
  await user.click(await screen.findByRole('button', { name: new RegExp(name) }));
}

describe('CompareTab', () => {
  it('shows the empty-selection state once the catalog is loaded', async () => {
    vi.mocked(pillar3Api.getProducts).mockResolvedValue(catalog);
    renderWithProviders(<CompareTab />);

    expect(
      await screen.findByText(/Search for 3a products above to compare/),
    ).toBeInTheDocument();
    expect(pillar3Api.compare).not.toHaveBeenCalled();
  });

  it('shows the empty-catalog state and disables the search when no products exist', async () => {
    vi.mocked(pillar3Api.getProducts).mockResolvedValue([]);
    renderWithProviders(<CompareTab />);

    expect(
      await screen.findByText(/No products available yet/),
    ).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Add product' })).toBeDisabled();
  });

  it('compares two selected products and marks the best one with the Top badge', async () => {
    vi.mocked(pillar3Api.getProducts).mockResolvedValue(catalog);
    mockCompareFromRequest();
    const user = userEvent.setup();
    renderWithProviders(<CompareTab />);

    await screen.findByText(/Search for 3a products above/);
    await selectProduct(user, 'Alpha Fund');
    await selectProduct(user, 'Beta Fund');

    // Both products appear in the desktop table and in the mobile card list.
    expect(await screen.findAllByText('Beta Fund')).toHaveLength(2);
    expect(screen.getAllByText('Alpha Fund')).toHaveLength(2);

    // Defaults are sent with both product ids (sorted for a stable key).
    await waitFor(() =>
      expect(pillar3Api.compare).toHaveBeenLastCalledWith('test-token', {
        currentBalance: 0,
        annualContribution: 7258,
        years: 20,
        productIds: ['a', 'b'],
      }),
    );

    // The «Top» badge sits in the row of the highest final capital.
    const table = screen.getByRole('table');
    const topRow = within(table).getByText('Top').closest('tr');
    expect(topRow).not.toBeNull();
    expect(within(topRow as HTMLElement).getByText('Beta Fund')).toBeInTheDocument();

    // …and in the matching mobile card.
    const list = screen.getByRole('list');
    const topItem = within(list).getByText('Top').closest('li');
    expect(topItem).not.toBeNull();
    expect(within(topItem as HTMLElement).getByText('Beta Fund')).toBeInTheDocument();

    expect(screen.getByText(/Total paid in: CHF 145'160\.00/)).toBeInTheDocument();
    expect(screen.getByText(/Maximum annual contribution: CHF 7'258\.00/)).toBeInTheDocument();
  });

  it('removes a product from the comparison', async () => {
    vi.mocked(pillar3Api.getProducts).mockResolvedValue(catalog);
    mockCompareFromRequest();
    const user = userEvent.setup();
    renderWithProviders(<CompareTab />);

    await screen.findByText(/Search for 3a products above/);
    await selectProduct(user, 'Alpha Fund');
    await selectProduct(user, 'Beta Fund');
    await screen.findAllByText('Top');

    // The remove action exists once per layout variant (table + mobile card).
    await user.click(screen.getAllByRole('button', { name: 'Remove Alpha Fund' })[0]);

    await waitFor(() => expect(screen.queryByText('Alpha Fund')).not.toBeInTheDocument());
    expect(pillar3Api.compare).toHaveBeenLastCalledWith(
      'test-token',
      expect.objectContaining({ productIds: ['b'] }),
    );
    expect(screen.getAllByText('Beta Fund').length).toBeGreaterThan(0);
  });

  it('returns to the empty-selection state when the last product is removed', async () => {
    vi.mocked(pillar3Api.getProducts).mockResolvedValue(catalog);
    mockCompareFromRequest();
    const user = userEvent.setup();
    renderWithProviders(<CompareTab />);

    await screen.findByText(/Search for 3a products above/);
    await selectProduct(user, 'Alpha Fund');
    await screen.findAllByText('Top');

    await user.click(screen.getAllByRole('button', { name: 'Remove Alpha Fund' })[0]);

    expect(await screen.findByText(/Search for 3a products above/)).toBeInTheDocument();
    expect(screen.queryByText('Top')).not.toBeInTheDocument();
  });
});
