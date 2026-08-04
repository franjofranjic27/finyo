import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AllocationDonut } from './AllocationDonut';
import { portfolioPosition } from '@/test/fixtures/portfolio';
import { formatCHF } from '@/lib/formatters';
import { CHART_COLOURS } from '@/lib/chartColours';

// Recharts' ResponsiveContainer renders 0x0 in jsdom, so SVG internals cannot
// be asserted — the card shell, the HTML legend list and the header controls can.

const positions = [
  portfolioPosition({ id: 'p1', name: 'Nestlé SA', assetClass: 'STOCK', value: 600, allocationPct: 60 }),
  portfolioPosition({ id: 'p2', name: 'iShares Core SPI', assetClass: 'ETF', value: 400, allocationPct: 40 }),
];

describe('AllocationDonut', () => {
  it('renders the allocation card title', () => {
    render(<AllocationDonut positions={positions} />);

    expect(screen.getByText('Allocation')).toBeInTheDocument();
  });

  it('shows asset-class groups by default with label, CHF value and share', () => {
    render(<AllocationDonut positions={positions} />);

    const items = screen.getAllByTestId('donut-legend-item');
    expect(items).toHaveLength(2);
    // ASSET_CLASSES display order: ETF before STOCK.
    expect(within(items[0]).getByText('ETF')).toBeInTheDocument();
    expect(within(items[0]).getByText(`${formatCHF(400)} · 40.0%`)).toBeInTheDocument();
    expect(within(items[1]).getByText('Individual stocks')).toBeInTheDocument();
    expect(within(items[1]).getByText(`${formatCHF(600)} · 60.0%`)).toBeInTheDocument();
  });

  it('exposes each group legend entry as a button reachable by role', () => {
    render(<AllocationDonut positions={positions} />);

    expect(screen.getByRole('button', { name: /^ETF/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Individual stocks/ })).toBeInTheDocument();
  });

  it('switches to the per-position view via the header chip', async () => {
    const user = userEvent.setup();
    render(<AllocationDonut positions={positions} />);

    await user.click(screen.getByRole('button', { name: 'All positions' }));

    const items = screen.getAllByTestId('donut-legend-item');
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByText('Nestlé SA')).toBeInTheDocument();
    expect(within(items[0]).getByText('60.0%')).toBeInTheDocument();
    expect(within(items[1]).getByText('iShares Core SPI')).toBeInTheDocument();
    expect(within(items[1]).getByText('40.0%')).toBeInTheDocument();
  });

  it('drills down into a group via its legend button and offers a way back', async () => {
    const user = userEvent.setup();
    render(<AllocationDonut positions={positions} />);

    await user.click(screen.getByRole('button', { name: /Individual stocks/ }));

    const items = screen.getAllByTestId('donut-legend-item');
    expect(items).toHaveLength(1);
    expect(within(items[0]).getByText('Nestlé SA')).toBeInTheDocument();
    // The chip row is replaced by the back button: visible label is the group's name,
    // the accessible name says where it leads.
    const backButton = screen.getByRole('button', { name: 'Back to groups' });
    expect(backButton).toHaveTextContent('Individual stocks');
    expect(screen.queryByRole('button', { name: 'All positions' })).not.toBeInTheDocument();
  });

  it('returns to the groups view via the back button', async () => {
    const user = userEvent.setup();
    render(<AllocationDonut positions={positions} />);

    await user.click(screen.getByRole('button', { name: /Individual stocks/ }));
    await user.click(screen.getByRole('button', { name: 'Back to groups' }));

    const items = screen.getAllByTestId('donut-legend-item');
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByText('ETF')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'All positions' })).toBeInTheDocument();
  });

  it('keeps the global index colour of a position in the drilldown', async () => {
    const user = userEvent.setup();
    render(<AllocationDonut positions={positions} />);

    // iShares is first in its ETF group, but second in the full list — its dot must
    // keep the global colour so it still matches the table dots and the positions donut.
    await user.click(screen.getByRole('button', { name: /^ETF/ }));

    const items = screen.getAllByTestId('donut-legend-item');
    expect(items).toHaveLength(1);
    expect(within(items[0]).getByText('iShares Core SPI')).toBeInTheDocument();
    expect(items[0].querySelector('span[aria-hidden="true"]')).toHaveStyle({
      backgroundColor: CHART_COLOURS[1],
    });
  });

  it('falls back to the groups view when the drilled group vanishes', async () => {
    const user = userEvent.setup();
    const { rerender } = render(<AllocationDonut positions={positions} />);

    await user.click(screen.getByRole('button', { name: /Individual stocks/ }));
    expect(screen.getByRole('button', { name: 'Back to groups' })).toBeInTheDocument();

    rerender(<AllocationDonut positions={[positions[1]]} />);

    const items = screen.getAllByTestId('donut-legend-item');
    expect(items).toHaveLength(1);
    expect(within(items[0]).getByText('ETF')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Back to groups' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'All positions' })).toBeInTheDocument();
  });

  it('truncates legend names so they cannot overflow the card', async () => {
    const user = userEvent.setup();
    render(<AllocationDonut positions={positions} />);

    expect(screen.getByText('Individual stocks')).toHaveClass('truncate', 'min-w-0');

    await user.click(screen.getByRole('button', { name: 'All positions' }));
    expect(screen.getByText('Nestlé SA')).toHaveClass('truncate', 'min-w-0');
  });

  it('shows the empty state when there are no positions', () => {
    render(<AllocationDonut positions={[]} />);

    expect(screen.getByText('No positions for the allocation yet')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
