import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronLeft, ChevronRight, SlidersHorizontal, Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { CardEmptyState } from '@/components/CardEmptyState';
import { PlanVsActualRow } from '@/components/budget/PlanVsActualRow';
import { derivePlanVsActual, hasBudgetPlan } from '@/components/budget/planVsActual';
import type { RangeCategoryBreakdown } from '@/api/analytics';
import type { MonthlyBudget } from '@/api/budget';
import {
  useMonthCategoryBreakdown,
  useMonthlyBudget,
  useMonthSummary,
} from '@/hooks/financeQueries';
import type { SpendingSummary } from '@/types';
import { currentMonth, monthLabel, monthRange, shiftMonth } from '@/lib/dates';
import { amountColour, formatCHF, formatPercent } from '@/lib/formatters';
import { CategoryRulesDialog } from './CategoryRulesDialog';
import { MonthTransactionsTable } from './MonthTransactionsTable';
import { StatementImportDialog } from './StatementImportDialog';

/** Month overview: summary, plan-vs-actual, category breakdown and transactions. */
export function MonthTab() {
  const { t, i18n } = useTranslation();

  const [month, setMonth] = useState(currentMonth);
  const [importOpen, setImportOpen] = useState(false);
  const [rulesOpen, setRulesOpen] = useState(false);
  const { from, to } = monthRange(month);

  const summary = useMonthSummary(month);
  const breakdown = useMonthCategoryBreakdown(month);
  const budget = useMonthlyBudget();

  const locale = i18n.language === 'de' ? 'de-CH' : 'en-GB';
  const isError = summary.isError || breakdown.isError || budget.isError;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            aria-label={t('budget.month.prevMonth')}
            onClick={() => setMonth((current) => shiftMonth(current, -1))}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="min-w-36 text-center text-sm font-semibold">
            {monthLabel(month, locale)}
          </span>
          <Button
            variant="ghost"
            size="icon"
            aria-label={t('budget.month.nextMonth')}
            onClick={() => setMonth((current) => shiftMonth(current, 1))}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
        <div className="ml-auto flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => setRulesOpen(true)}>
            <SlidersHorizontal className="mr-1 h-4 w-4" />
            {t('budget.month.rules')}
          </Button>
          <Button size="sm" onClick={() => setImportOpen(true)}>
            <Upload className="mr-1 h-4 w-4" />
            {t('budget.month.importCta')}
          </Button>
        </div>
      </div>

      {isError && <p className="text-sm text-destructive">{t('budget.month.loadError')}</p>}
      {summary.isLoading && <Skeleton className="h-24 w-full" />}

      {summary.data && <MonthSummaryCards summary={summary.data} />}

      <div className="grid items-start gap-4 lg:grid-cols-2">
        {budget.data && summary.data && (
          <MonthBudgetComparisonCard budget={budget.data} summary={summary.data} />
        )}
        {breakdown.data && <MonthCategoryBreakdown items={breakdown.data} />}
      </div>

      {/* Keyed by month so paging state resets when the month changes. */}
      <MonthTransactionsTable
        key={month}
        from={from}
        to={to}
        onImportClick={() => setImportOpen(true)}
      />

      <StatementImportDialog open={importOpen} onOpenChange={setImportOpen} />
      {rulesOpen && <CategoryRulesDialog onClose={() => setRulesOpen(false)} />}
    </div>
  );
}

function MonthSummaryCards({ summary }: Readonly<{ summary: SpendingSummary }>) {
  const { t } = useTranslation();
  const income = Number(summary.totalIncome);
  const expenses = Number(summary.totalExpenses);
  const balance = Number(summary.netAmount);

  const tiles = [
    { key: 'income', value: income, colour: 'text-emerald-500', prefix: '+' },
    { key: 'expenses', value: expenses, colour: 'text-red-500', prefix: '−' },
    { key: 'balance', value: balance, colour: amountColour(balance), prefix: '' },
  ] as const;

  return (
    <div className="grid gap-4 sm:grid-cols-3">
      {tiles.map(({ key, value, colour, prefix }) => (
        <Card key={key}>
          <CardContent className="pt-6">
            <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {t(`budget.month.${key}`)}
            </p>
            <p className={`mt-1 text-2xl font-bold tabular-nums ${colour}`}>
              {prefix}
              {formatCHF(Math.abs(value))}
            </p>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

function MonthBudgetComparisonCard({
  budget,
  summary,
}: Readonly<{ budget: MonthlyBudget; summary: SpendingSummary }>) {
  const { t } = useTranslation();

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('budget.month.planVsActual')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        {hasBudgetPlan(budget) ? (
          <PlanVsActualBody budget={budget} summary={summary} />
        ) : (
          // Without a planned net income every bar would read CHF 0.00 / 0.0 %.
          <CardEmptyState
            message={t('budget.month.noPlan')}
            ctaLabel={t('budget.month.planCta')}
            to="/budget?tab=plan"
          />
        )}
      </CardContent>
    </Card>
  );
}

function PlanVsActualBody({
  budget,
  summary,
}: Readonly<{ budget: MonthlyBudget; summary: SpendingSummary }>) {
  const { t } = useTranslation();
  const comparison = derivePlanVsActual(budget, summary);

  return (
    <>
      <PlanVsActualRow
        label={t('budget.month.plannedIncome')}
        planned={comparison.plannedIncome}
        actual={comparison.actualIncome}
        overIsBad={false}
      />
      <PlanVsActualRow
        label={t('budget.month.available')}
        hint={t('budget.month.availableHint')}
        planned={comparison.plannedVariable}
        actual={comparison.actualVariable}
        overIsBad
      />
      <div className="flex items-baseline justify-between border-t border-border pt-3 text-sm">
        <span>
          <span className="font-medium">{t('budget.month.fixedCosts')}</span>
          <span className="ml-2 text-xs text-muted-foreground">
            {t('budget.month.fixedCostsHint')}
          </span>
        </span>
        <span className="tabular-nums">{formatCHF(comparison.fixedCosts)}</span>
      </div>
    </>
  );
}

function MonthCategoryBreakdown({ items }: Readonly<{ items: RangeCategoryBreakdown[] }>) {
  const { t } = useTranslation();

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('budget.month.byCategory')}</CardTitle>
      </CardHeader>
      <CardContent>
        {items.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">{t('common.noData')}</p>
        ) : (
          <ul className="space-y-2.5">
            {items.map((item) => (
              <li
                key={item.categoryId ?? 'uncategorized'}
                className="flex items-center gap-2 text-sm"
              >
                <span
                  data-testid="category-swatch"
                  className="h-2.5 w-2.5 shrink-0 rounded-full bg-muted-foreground"
                  style={item.categoryColor ? { backgroundColor: item.categoryColor } : undefined}
                />
                {/* the backend labels the null-category group "Uncategorized" in English —
                    key off the id so the label follows the UI language */}
                <span className="min-w-0 truncate">
                  {item.categoryId === null
                    ? t('budget.month.uncategorized')
                    : item.categoryName}
                </span>
                <span className="ml-auto shrink-0 tabular-nums text-muted-foreground">
                  {formatPercent(item.percentage)}
                </span>
                <span className="w-28 shrink-0 text-right font-medium tabular-nums">
                  {formatCHF(item.total)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
