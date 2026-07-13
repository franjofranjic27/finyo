import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { CardEmptyState } from '@/components/CardEmptyState';
import { PlanVsActualRow } from '@/components/budget/PlanVsActualRow';
import { derivePlanVsActual, hasBudgetPlan } from '@/components/budget/planVsActual';
import type { MonthlyBudget } from '@/api/budget';
import { useMonthlyBudget, useMonthSummary } from '@/hooks/financeQueries';
import type { SpendingSummary } from '@/types';
import { formatCHF } from '@/lib/formatters';

function BudgetStatusContent({
  budget,
  summary,
}: Readonly<{ budget: MonthlyBudget; summary: SpendingSummary }>) {
  const { t } = useTranslation();

  if (!hasBudgetPlan(budget)) {
    return (
      <CardEmptyState
        message={t('dashboard.empty.noBudget')}
        ctaLabel={t('dashboard.empty.setupBudget')}
        to="/budget"
      />
    );
  }

  if (summary.transactionCount === 0) {
    return (
      <CardEmptyState
        message={t('dashboard.empty.noTransactions')}
        ctaLabel={t('dashboard.empty.importStatement')}
        to="/budget?tab=month"
      />
    );
  }

  const comparison = derivePlanVsActual(budget, summary);

  return (
    <div className="space-y-5">
      <PlanVsActualRow
        label={t('dashboard.incomeOfPlan')}
        planned={comparison.plannedIncome}
        actual={comparison.actualIncome}
        overIsBad={false}
      />
      <PlanVsActualRow
        label={t('dashboard.spentOfAvailable')}
        hint={t('budget.month.availableHint')}
        planned={comparison.plannedVariable}
        actual={comparison.actualVariable}
        overIsBad
      />
      <div className="flex items-baseline justify-between border-t border-border pt-3 text-sm">
        <span className="font-medium">{t('budget.month.fixedCosts')}</span>
        <span className="tabular-nums">{formatCHF(comparison.fixedCosts)}</span>
      </div>
    </div>
  );
}

/** Plan vs. actual of the running month, based on the monthly budget plan. */
export function BudgetStatusCard() {
  const { t } = useTranslation();
  const budget = useMonthlyBudget();
  const summary = useMonthSummary();

  const isLoading = budget.isLoading || summary.isLoading;
  const isError = budget.isError || summary.isError;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('dashboard.budgetStatus')}</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading && <Skeleton className="h-40 w-full" />}
        {isError && <p className="text-sm text-destructive">{t('dashboard.loadError')}</p>}
        {budget.data && summary.data && (
          <BudgetStatusContent budget={budget.data} summary={summary.data} />
        )}
      </CardContent>
    </Card>
  );
}
