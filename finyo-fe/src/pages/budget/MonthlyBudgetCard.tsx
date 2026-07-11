import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Car, Pencil, PiggyBank, ReceiptText, RefreshCw, ShieldCheck, TrendingUp, Wallet,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import type { MonthlyBudget } from '@/api/budget';
import { formatCHF, formatPercent } from '@/lib/formatters';
import { MonthlyBudgetDialog } from './MonthlyBudgetDialog';

interface FlowRow {
  key: 'netIncome' | 'savings' | 'investing' | 'pillar3a' | 'taxReserve' | 'fixedCosts' | 'workCosts';
  icon: LucideIcon;
  value: number;
  direction: 'in' | 'out';
}

function buildRows(budget: MonthlyBudget): FlowRow[] {
  return [
    { key: 'netIncome', icon: Wallet, value: budget.netIncome, direction: 'in' },
    { key: 'savings', icon: PiggyBank, value: budget.savings, direction: 'out' },
    { key: 'investing', icon: TrendingUp, value: budget.investing, direction: 'out' },
    { key: 'pillar3a', icon: ShieldCheck, value: budget.pillar3a, direction: 'out' },
    { key: 'taxReserve', icon: ReceiptText, value: budget.taxReserve, direction: 'out' },
    { key: 'fixedCosts', icon: RefreshCw, value: budget.fixedCostsPerMonth, direction: 'out' },
    { key: 'workCosts', icon: Car, value: budget.workCosts, direction: 'out' },
  ];
}

function AvailableTile({ budget }: Readonly<{ budget: MonthlyBudget }>) {
  const { t } = useTranslation();
  const negative = budget.available < 0;

  return (
    <div
      className={`mt-4 rounded-lg border p-4 text-center ${
        negative ? 'border-red-500/35 bg-red-500/10' : 'border-emerald-500/35 bg-emerald-500/10'
      }`}
    >
      <p
        className={`text-xs font-semibold uppercase tracking-wider ${
          negative ? 'text-red-600' : 'text-emerald-600'
        }`}
      >
        {t('budget.monthly.available')}
      </p>
      <p
        className={`mt-1 text-3xl font-bold tabular-nums ${
          negative ? 'text-red-500' : 'text-emerald-500'
        }`}
      >
        {formatCHF(budget.available)}
      </p>
      {budget.netIncome !== 0 && (
        <p className="mt-1 text-xs text-muted-foreground">
          {t('budget.monthly.availableHint', {
            pct: formatPercent((budget.available / budget.netIncome) * 100),
          })}
        </p>
      )}
    </div>
  );
}

export function MonthlyBudgetCard({ budget }: Readonly<{ budget: MonthlyBudget }>) {
  const { t } = useTranslation();
  const [editing, setEditing] = useState(false);

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between space-y-0">
        <div>
          <CardTitle className="text-base">{t('budget.monthly.title')}</CardTitle>
          <p className="mt-1 text-xs text-muted-foreground">{t('budget.monthly.subtitle')}</p>
        </div>
        <Button variant="ghost" size="sm" onClick={() => setEditing(true)}>
          <Pencil className="mr-1 h-4 w-4" />
          {t('budget.monthly.edit')}
        </Button>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col">
          {buildRows(budget).map(({ key, icon: Icon, value, direction }) => (
            <div
              key={key}
              className="flex items-center gap-3 border-b border-border py-2.5 text-sm last:border-0"
            >
              <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-secondary">
                <Icon className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
              </span>
              <span className="min-w-0">
                <span className="block truncate font-semibold">{t(`budget.monthly.${key}`)}</span>
                <span className="block truncate text-xs text-muted-foreground">
                  {t(`budget.monthly.${key}Hint`)}
                </span>
              </span>
              <span
                className={`ml-auto font-semibold tabular-nums ${
                  direction === 'in' ? 'text-emerald-500' : 'text-red-500'
                }`}
              >
                {direction === 'in' ? '+' : '−'}
                {formatCHF(value)}
              </span>
            </div>
          ))}
        </div>
        <AvailableTile budget={budget} />
      </CardContent>

      {editing && <MonthlyBudgetDialog budget={budget} onClose={() => setEditing(false)} />}
    </Card>
  );
}
