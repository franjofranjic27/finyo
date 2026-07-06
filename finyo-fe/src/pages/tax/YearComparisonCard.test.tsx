import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { YearComparisonCard } from './YearComparisonCard';
import { taxYearSummary } from '@/test/fixtures/tax';

const fullYear = (year: number) =>
  taxYearSummary({
    year,
    grossIncome: 100_000 + year,
    expectedTax: 15_000,
    effectiveRatePercent: 15,
  });

describe('YearComparisonCard', () => {
  it('renders nothing with fewer than two comparable years', () => {
    const { container } = render(
      <YearComparisonCard years={[fullYear(2024), taxYearSummary({ year: 2023 })]} />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('renders the comparison chart with two or more comparable years', () => {
    render(<YearComparisonCard years={[fullYear(2024), fullYear(2023)]} />);

    expect(screen.getByText('Year Comparison')).toBeInTheDocument();
  });
});
