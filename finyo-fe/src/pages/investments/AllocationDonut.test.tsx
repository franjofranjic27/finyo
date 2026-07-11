import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { AllocationDonut } from './AllocationDonut';
import { portfolioPosition } from '@/test/fixtures/portfolio';

// Recharts' ResponsiveContainer renders 0x0 in jsdom, so SVG internals cannot
// be asserted — the card shell and the HTML legend list can.

const positions = [
  portfolioPosition({ id: 'p1', name: 'Nestlé SA', allocationPct: 60 }),
  portfolioPosition({ id: 'p2', name: 'iShares Core SPI', allocationPct: 40 }),
];

describe('AllocationDonut', () => {
  it('renders the allocation card title', () => {
    render(<AllocationDonut positions={positions} />);

    expect(screen.getByText('Allocation')).toBeInTheDocument();
  });

  it('renders a legend list entry per position with name and share', () => {
    render(<AllocationDonut positions={positions} />);

    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByText('Nestlé SA')).toBeInTheDocument();
    expect(within(items[0]).getByText('60.0%')).toBeInTheDocument();
    expect(within(items[1]).getByText('iShares Core SPI')).toBeInTheDocument();
    expect(within(items[1]).getByText('40.0%')).toBeInTheDocument();
  });

  it('truncates legend names so they cannot overflow the card', () => {
    render(<AllocationDonut positions={positions} />);

    expect(screen.getByText('Nestlé SA')).toHaveClass('truncate', 'min-w-0');
  });

  it('shows the empty state when there are no positions', () => {
    render(<AllocationDonut positions={[]} />);

    expect(screen.getByText('No positions for the allocation yet')).toBeInTheDocument();
  });
});
