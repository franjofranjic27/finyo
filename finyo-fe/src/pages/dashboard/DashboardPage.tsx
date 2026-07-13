import { useTranslation } from 'react-i18next';
import { Tag, TrendingDown, TrendingUp, Wallet } from 'lucide-react';
import { amountColour, formatCHF } from '@/lib/formatters';
import { BudgetStatusCard } from './BudgetStatusCard';
import { MonthlyTrendCard } from './MonthlyTrendCard';
import { SpendingDonutCard } from './SpendingDonutCard';
import { SummaryCard, SummaryCardSkeleton } from './SummaryCard';
import { WealthSummaryCard } from './WealthSummaryCard';
import { biggestCategory, categoryAmount, categoryLabel } from './categoryBreakdown';
import { useMonthCategoryBreakdown, useMonthSummary } from './dashboardQueries';

const SUMMARY_SKELETON_KEYS = ['net', 'income', 'expenses', 'category'];

/** KPI tiles of the running month: income, expenses, balance and top category. */
function MonthKpiRow() {
  const { t } = useTranslation();
  const summary = useMonthSummary();
  const breakdown = useMonthCategoryBreakdown();

  if (summary.isLoading || breakdown.isLoading) {
    return (
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {SUMMARY_SKELETON_KEYS.map((key) => (
          <SummaryCardSkeleton key={key} />
        ))}
      </div>
    );
  }

  if (summary.isError || breakdown.isError || !summary.data || !breakdown.data) {
    return <p className="text-sm text-destructive">{t('budget.month.loadError')}</p>;
  }

  const { totalIncome, totalExpenses, netAmount, transactionCount } = summary.data;
  const topCategory = breakdown.data.length > 0 ? biggestCategory(breakdown.data) : null;

  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <SummaryCard
        title={t('dashboard.netThisMonth')}
        value={formatCHF(netAmount)}
        icon={Wallet}
        valueClass={amountColour(netAmount)}
        sub={t('dashboard.transactionCount', { count: transactionCount })}
      />
      <SummaryCard
        title={t('dashboard.totalIncome')}
        value={formatCHF(totalIncome)}
        icon={TrendingUp}
        valueClass="text-emerald-500"
      />
      <SummaryCard
        title={t('dashboard.totalSpent')}
        value={formatCHF(totalExpenses)}
        icon={TrendingDown}
        valueClass="text-red-500"
      />
      <SummaryCard
        title={t('dashboard.biggestCategory')}
        value={
          topCategory ? categoryLabel(topCategory, t('budget.month.uncategorized')) : '—'
        }
        icon={Tag}
        sub={topCategory ? formatCHF(categoryAmount(topCategory)) : undefined}
      />
    </div>
  );
}

/**
 * Dashboard: wealth and its allocation on top, budget status of the running
 * month next to it, then the month's KPIs and charts. Every figure comes from
 * the API — cards render their own empty state when there is no data yet.
 */
export function DashboardPage() {
  return (
    <div className="space-y-6">
      <div className="grid items-start gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <WealthSummaryCard />
        </div>
        <BudgetStatusCard />
      </div>

      <MonthKpiRow />

      <div className="grid items-start gap-4 lg:grid-cols-2">
        <SpendingDonutCard />
        <MonthlyTrendCard />
      </div>
    </div>
  );
}
