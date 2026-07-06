import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MonthlyPaymentsCard } from './MonthlyPaymentsCard';
import { taxYearDetail } from '@/test/fixtures/tax';

describe('MonthlyPaymentsCard', () => {
  it('shows the payment progress and the open amount', () => {
    const detail = taxYearDetail({
      year: 2025,
      expectedTax: 10_000,
      paidTotal: 5000,
      openAmount: 5000,
      payments: [{ id: 'p1', paymentDate: '2025-02-01', amount: 5000, label: 'Q1' }],
    });

    render(<MonthlyPaymentsCard detail={detail} />);

    expect(screen.getByText('Monthly Payments')).toBeInTheDocument();
    expect(screen.getByText(/50 % paid/)).toBeInTheDocument();
    expect(screen.getByText(/CHF 5'000\.00/)).toBeInTheDocument();
  });

  it('shows 0 % paid when the expected tax is unknown', () => {
    const detail = taxYearDetail({ year: 2025, expectedTax: null, paidTotal: 3000 });

    render(<MonthlyPaymentsCard detail={detail} />);

    expect(screen.getByText(/0 % paid/)).toBeInTheDocument();
    expect(screen.getByText(/CHF 0\.00/)).toBeInTheDocument();
  });
});
