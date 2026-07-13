import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, SlidersHorizontal, Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { analyticsApi } from '@/api/analytics';
import type { RangeCategoryBreakdown } from '@/api/analytics';
import { budgetApi } from '@/api/budget';
import type { MonthlyBudget } from '@/api/budget';
import type { SpendingSummary } from '@/types';
import { currentMonth, monthLabel, monthRange, shiftMonth } from '@/lib/dates';
import { amountColour, formatCHF, formatPercent } from '@/lib/formatters';
import { CategoryRulesDialog } from './CategoryRulesDialog';
import { MonthTransactionsTable } from './MonthTransactionsTable';
import { StatementImportDialog } from './StatementImportDialog';

/** Month overview: summary, plan-vs-actual, category breakdown and transactions. */
export function MonthTab() {
  const { t, i18n } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const [month, setMonth] = useState(currentMonth);
  const [importOpen, setImportOpen] = useState(false);
  const [rulesOpen, setRulesOpen] = useState(false);
  const { from, to } = monthRange(month);

  const summary = useQuery({
    queryKey: ['analytics', 'summary', from, to],
    queryFn: () => analyticsApi.getSummaryRange(token, from, to),
    enabled: !!token,
  });

  const breakdown = useQuery({
    queryKey: ['analytics', 'categories', from, to],
    queryFn: () => analyticsApi.getCategoryBreakdownRange(token, from, to),
    enabled: !!token,
  });

  const budget = useQuery({
    queryKey: ['monthly-budget'],
    queryFn: () => budgetApi.getMonthlyBudget(token),
    enabled: !!token,
  });

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

interface ComparisonRowProps {
  label: string;
  hint?: string;
  planned: number;
  actual: number;
  /** For expenses "over plan" is bad; for income "under plan" is the shortfall. */
  overIsBad: boolean;
}

function ComparisonRow({ label, hint, planned, actual, overIsBad }: Readonly<ComparisonRowProps>) {
  const { t } = useTranslation();
  const pct = planned > 0 ? (actual / planned) * 100 : 0;
  const over = overIsBad && actual > planned;

  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between gap-2 text-sm">
        <span className="font-medium">{label}</span>
        <span className={`tabular-nums ${over ? 'font-semibold text-destructive' : ''}`}>
          {formatCHF(actual)}
          <span className="text-muted-foreground">
            {' '}
            / {formatCHF(planned)} {t('budget.month.plan')}
          </span>
        </span>
      </div>
      <Progress
        value={Math.min(100, Math.max(0, pct))}
        className={over ? '[&>div]:bg-destructive' : ''}
      />
      <div className="flex justify-between text-xs text-muted-foreground">
        <span>{hint}</span>
        <span className={over ? 'font-medium text-destructive' : ''}>{formatPercent(pct)}</span>
      </div>
    </div>
  );
}

function MonthBudgetComparisonCard({
  budget,
  summary,
}: Readonly<{ budget: MonthlyBudget; summary: SpendingSummary }>) {
  const { t } = useTranslation();
  const actualIncome = Number(summary.totalIncome);
  const actualExpenses = Number(summary.totalExpenses);
  // Fixed costs are budgeted separately, so only the variable share counts
  // against the plan's available amount.
  const actualVariable = Math.max(0, actualExpenses - budget.fixedCostsPerMonth);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('budget.month.planVsActual')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        <ComparisonRow
          label={t('budget.month.plannedIncome')}
          planned={budget.netIncome}
          actual={actualIncome}
          overIsBad={false}
        />
        <ComparisonRow
          label={t('budget.month.available')}
          hint={t('budget.month.availableHint')}
          planned={Math.max(0, budget.available)}
          actual={actualVariable}
          overIsBad
        />
        <div className="flex items-baseline justify-between border-t border-border pt-3 text-sm">
          <span>
            <span className="font-medium">{t('budget.month.fixedCosts')}</span>
            <span className="ml-2 text-xs text-muted-foreground">
              {t('budget.month.fixedCostsHint')}
            </span>
          </span>
          <span className="tabular-nums">{formatCHF(budget.fixedCostsPerMonth)}</span>
        </div>
      </CardContent>
    </Card>
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
                <span className="min-w-0 truncate">
                  {item.categoryName ?? t('budget.month.uncategorized')}
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
