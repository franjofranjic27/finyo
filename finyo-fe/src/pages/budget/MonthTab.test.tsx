import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MonthTab } from './MonthTab';
import { analyticsApi } from '@/api/analytics';
import { budgetApi } from '@/api/budget';
import { renderWithProviders } from '@/test/test-utils';
import { monthlyBudget } from '@/test/fixtures/budget';
import { currentMonth, monthRange, shiftMonth } from '@/lib/dates';
import type { SpendingSummary } from '@/types';

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

vi.mock('@/api/analytics', () => ({
  analyticsApi: {
    getSummaryRange: vi.fn(),
    getCategoryBreakdownRange: vi.fn(),
  },
}));

vi.mock('@/api/budget', () => ({
  budgetApi: {
    getMonthlyBudget: vi.fn(),
  },
}));

// Children have their own suites — stub them to keep this one focused.
vi.mock('./MonthTransactionsTable', () => ({
  MonthTransactionsTable: ({ from, to }: { from: string; to: string }) => (
    <div>
      transactions-table {from} {to}
    </div>
  ),
}));

vi.mock('./StatementImportDialog', () => ({
  StatementImportDialog: ({ open }: { open: boolean }) =>
    open ? <div>import-dialog</div> : null,
}));

vi.mock('./CategoryRulesDialog', () => ({
  CategoryRulesDialog: () => <div>rules-dialog</div>,
}));

function spendingSummary(overrides: Partial<SpendingSummary> = {}): SpendingSummary {
  return {
    totalIncome: '5268.90',
    totalExpenses: '3200.00',
    netAmount: '2068.90',
    rangeStart: '2026-07-01',
    rangeEnd: '2026-07-31',
    transactionCount: 12,
    ...overrides,
  };
}

describe('MonthTab', () => {
  beforeEach(() => {
    vi.mocked(analyticsApi.getSummaryRange).mockResolvedValue(spendingSummary());
    vi.mocked(analyticsApi.getCategoryBreakdownRange).mockResolvedValue([
      {
        categoryId: 'c1',
        categoryName: 'Lebensmittel',
        categoryColor: '#22c55e',
        total: '800.00',
        percentage: 25,
      },
      {
        categoryId: null,
        categoryName: null,
        categoryColor: null,
        total: '2400.00',
        percentage: 75,
      },
    ]);
    vi.mocked(budgetApi.getMonthlyBudget).mockResolvedValue(monthlyBudget());
  });

  it('renders summary cards, plan comparison and breakdown for the current month', async () => {
    renderWithProviders(<MonthTab />);

    expect(await screen.findByText('Income')).toBeInTheDocument();
    expect(screen.getByText("+CHF 5'268.90")).toBeInTheDocument();
    expect(screen.getByText("−CHF 3'200.00")).toBeInTheDocument();
    expect(screen.getByText("CHF 2'068.90")).toBeInTheDocument();

    expect(screen.getByText('Plan vs. actual')).toBeInTheDocument();
    expect(screen.getByText('Lebensmittel')).toBeInTheDocument();
    expect(screen.getByText('Uncategorised')).toBeInTheDocument();

    const { from, to } = monthRange(currentMonth());
    expect(analyticsApi.getSummaryRange).toHaveBeenCalledWith('test-token', from, to);
    expect(analyticsApi.getCategoryBreakdownRange).toHaveBeenCalledWith('test-token', from, to);
    expect(screen.getByText(`transactions-table ${from} ${to}`)).toBeInTheDocument();
  });

  it('navigates to the previous month and queries its range', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MonthTab />);
    await screen.findByText('Income');

    await user.click(screen.getByRole('button', { name: 'Previous month' }));

    const previous = monthRange(shiftMonth(currentMonth(), -1));
    expect(analyticsApi.getSummaryRange).toHaveBeenCalledWith(
      'test-token',
      previous.from,
      previous.to,
    );
    expect(
      await screen.findByText(`transactions-table ${previous.from} ${previous.to}`),
    ).toBeInTheDocument();
  });

  it('opens the statement import dialog from the header button', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MonthTab />);
    await screen.findByText('Income');

    expect(screen.queryByText('import-dialog')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Import bank statement' }));
    expect(screen.getByText('import-dialog')).toBeInTheDocument();
  });

  it('opens the category rules dialog from the header button', async () => {
    const user = userEvent.setup();
    renderWithProviders(<MonthTab />);
    await screen.findByText('Income');

    expect(screen.queryByText('rules-dialog')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Rules' }));
    expect(screen.getByText('rules-dialog')).toBeInTheDocument();
  });
});
