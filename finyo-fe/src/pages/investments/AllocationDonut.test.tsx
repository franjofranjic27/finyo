import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AllocationDonut } from './AllocationDonut';
import { portfolioPosition } from '@/test/fixtures/portfolio';

// Recharts' ResponsiveContainer renders 0x0 in jsdom, so SVG internals
// (slices, centre label, legend) cannot be asserted — only the card shell.

describe('AllocationDonut', () => {
  it('renders the allocation card title', () => {
    render(<AllocationDonut positions={[portfolioPosition({ id: 'p1' })]} />);

    expect(screen.getByText('Allocation')).toBeInTheDocument();
  });

  it('shows the empty state when there are no positions', () => {
    render(<AllocationDonut positions={[]} />);

    expect(screen.getByText('No positions for the allocation yet')).toBeInTheDocument();
  });
});
