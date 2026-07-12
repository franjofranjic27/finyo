import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { WealthKpiTiles } from './WealthKpiTiles';
import { wealthOverview } from '@/test/fixtures/wealth';

function renderTiles(overrides: Parameters<typeof wealthOverview>[0] = {}) {
  return render(<WealthKpiTiles overview={wealthOverview(overrides)} />);
}

describe('WealthKpiTiles', () => {
  it('shows the four KPI tiles with formatted amounts and hints', () => {
    renderTiles({
      total: 99_719,
      totalMonthlyRate: 1600,
      totalForecastYearEnd: 107_719,
      asOf: '2026-07-11',
    });

    expect(screen.getByText('Total wealth')).toBeInTheDocument();
    expect(screen.getByText("CHF 99'719.00")).toBeInTheDocument();
    expect(screen.getByText('as of 11 Jul 2026')).toBeInTheDocument();
    expect(screen.getByText('Monthly savings rate')).toBeInTheDocument();
    expect(screen.getByText("CHF 1'600.00")).toBeInTheDocument();
    expect(screen.getByText('across all buckets')).toBeInTheDocument();
    expect(screen.getByText('Forecast Dec 31')).toBeInTheDocument();
    expect(screen.getByText("CHF 107'719.00")).toBeInTheDocument();
    expect(screen.getByText('without return assumption')).toBeInTheDocument();
  });

  it('prefixes a positive YTD change with + and colours it green', () => {
    renderTiles({ ytdChange: 12_340, ytdChangePct: 14.1 });

    expect(screen.getByText("+CHF 12'340.00")).toHaveClass('text-emerald-500');
    expect(screen.getByText('+14.1%')).toHaveClass('text-emerald-500');
  });

  it('colours a negative YTD change red without + prefix', () => {
    renderTiles({ ytdChange: -800, ytdChangePct: -0.8 });

    expect(screen.getByText('CHF -800.00')).toHaveClass('text-red-500');
    expect(screen.getByText('-0.8%')).toHaveClass('text-red-500');
  });

  it('shows the placeholder when there is no YTD comparison data yet', () => {
    renderTiles({ ytdChange: null, ytdChangePct: null });

    expect(screen.getByText('—')).toBeInTheDocument();
    expect(screen.getByText('no comparison data yet')).toBeInTheDocument();
  });
});
