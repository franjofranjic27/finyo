import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { SpendingDonutCard } from './SpendingDonutCard';
import { analyticsApi } from '@/api/analytics';
import type { RangeCategoryBreakdown } from '@/api/analytics';
import { renderWithProviders } from '@/test/test-utils';
import { currentMonth, monthRange } from '@/lib/dates';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({ accessToken: 'test-token' }),
}));

vi.mock('@/api/analytics', () => ({
  analyticsApi: { getCategoryBreakdownRange: vi.fn() },
}));

const breakdown: RangeCategoryBreakdown[] = [
  {
    categoryId: 'c1',
    categoryName: 'Lebensmittel',
    categoryColor: '#22c55e',
    total: '800.00',
    percentage: 25,
  },
  {
    categoryId: null,
    categoryName: 'Uncategorized',
    categoryColor: null,
    total: '2400.00',
    percentage: 75,
  },
];

describe('SpendingDonutCard', () => {
  beforeEach(() => {
    vi.mocked(analyticsApi.getCategoryBreakdownRange).mockResolvedValue(breakdown);
  });

  it('shows a skeleton while the breakdown loads', () => {
    vi.mocked(analyticsApi.getCategoryBreakdownRange).mockReturnValue(new Promise(() => {}));
    const { container } = renderWithProviders(<SpendingDonutCard />);

    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders one donut slice per category and translates the null category', async () => {
    renderWithProviders(<SpendingDonutCard />);

    expect(await screen.findByText('Lebensmittel')).toBeInTheDocument();
    // the backend label "Uncategorized" must not leak into the UI
    expect(screen.getByText('Uncategorised')).toBeInTheDocument();
    expect(screen.queryByText('Uncategorized')).not.toBeInTheDocument();

    expect(screen.getAllByTestId('donut-slice')).toHaveLength(2);
    expect(screen.getByText('CHF 800.00')).toBeInTheDocument();
    expect(screen.getByText("CHF 2'400.00")).toBeInTheDocument();
    expect(screen.getByText('75.0%')).toBeInTheDocument();

    const { from, to } = monthRange(currentMonth());
    expect(analyticsApi.getCategoryBreakdownRange).toHaveBeenCalledWith('test-token', from, to);
  });

  it('links to the month view when there is nothing to show', async () => {
    vi.mocked(analyticsApi.getCategoryBreakdownRange).mockResolvedValue([]);
    renderWithProviders(<SpendingDonutCard />);

    const cta = await screen.findByRole('link', { name: /Import bank statement/ });
    expect(cta).toHaveAttribute('href', '/budget?tab=month');
    expect(screen.getByText('No transactions this month yet.')).toBeInTheDocument();
  });

  it('shows an inline error when the breakdown fails', async () => {
    vi.mocked(analyticsApi.getCategoryBreakdownRange).mockRejectedValue(new Error('boom'));
    renderWithProviders(<SpendingDonutCard />);

    expect(await screen.findByText('Could not load the month view')).toBeInTheDocument();
  });
});
