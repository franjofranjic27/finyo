import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { KpiTiles } from './KpiTiles';
import { portfolio } from '@/test/fixtures/portfolio';

function renderTiles(overrides: Parameters<typeof portfolio>[0] = {}) {
  return render(<KpiTiles portfolio={portfolio(overrides)} />);
}

describe('KpiTiles', () => {
  it('shows the four KPI tiles with formatted amounts', () => {
    renderTiles({ totalValue: 12_500.5, totalCost: 10_000, gainLoss: 2500.5, returnPct: 25 });

    expect(screen.getByText('Total Value')).toBeInTheDocument();
    expect(screen.getByText("CHF 12'500.50")).toBeInTheDocument();
    expect(screen.getByText('Invested')).toBeInTheDocument();
    expect(screen.getByText("CHF 10'000.00")).toBeInTheDocument();
    expect(screen.getByText('Gain / Loss')).toBeInTheDocument();
    expect(screen.getByText('Return')).toBeInTheDocument();
  });

  it('prefixes gains with + and colours them green', () => {
    renderTiles({ gainLoss: 2500.5, returnPct: 25 });

    expect(screen.getByText("+CHF 2'500.50")).toHaveClass('text-emerald-500');
    expect(screen.getByText('+25.0%')).toHaveClass('text-emerald-500');
  });

  it('shows losses without + prefix and colours them red', () => {
    renderTiles({ gainLoss: -800, returnPct: -8 });

    expect(screen.getByText('CHF -800.00')).toHaveClass('text-red-500');
    expect(screen.getByText('-8.0%')).toHaveClass('text-red-500');
  });

  it('warns that the total is partial when a position could not be converted', () => {
    renderTiles({ hasUnconverted: true });

    expect(
      screen.getByText(/no exchange rate yet and are not included in the total/i),
    ).toBeInTheDocument();
  });

  it('shows no partial-total warning when everything converted', () => {
    renderTiles({ hasUnconverted: false });

    expect(screen.queryByText(/not included in the total/i)).not.toBeInTheDocument();
  });
});
