import { useTranslation } from 'react-i18next';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import {
  chartAxisProps,
  chartGridProps,
  chartTooltipStyle,
  formatThousandsTick,
  formatTooltipCHF,
  renderLegendText,
} from '@/components/charts/chartStyle';
import { CHART_COLOURS } from '@/lib/chartColours';
import type { MonthlyDataPoint } from '@/types';
import { CardEmptyState } from './CardEmptyState';
import { useMonthlyTrend } from './dashboardQueries';

const CHART_HEIGHT = 260;
const INCOME_COLOUR = '#10b981';
const EXPENSE_COLOUR = CHART_COLOURS[0];
const BAR_RADIUS: [number, number, number, number] = [3, 3, 0, 0];

interface TrendBar {
  month: string;
  income: number;
  expenses: number;
}

function toTrendBars(points: readonly MonthlyDataPoint[]): TrendBar[] {
  return points.map((point) => ({
    month: `${String(point.month).padStart(2, '0')}/${point.year}`,
    income: Math.abs(Number(point.income)),
    expenses: Math.abs(Number(point.expenses)),
  }));
}

function hasMovement(bars: readonly TrendBar[]): boolean {
  return bars.some((bar) => bar.income !== 0 || bar.expenses !== 0);
}

function TrendChart({ bars }: Readonly<{ bars: TrendBar[] }>) {
  const { t } = useTranslation();

  return (
    <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
      <BarChart data={bars} barGap={4}>
        <CartesianGrid {...chartGridProps} />
        <XAxis dataKey="month" {...chartAxisProps} />
        <YAxis {...chartAxisProps} tickFormatter={formatThousandsTick} />
        <Tooltip formatter={formatTooltipCHF} contentStyle={chartTooltipStyle} />
        <Legend formatter={renderLegendText} />
        <Bar
          dataKey="income"
          name={t('budget.month.income')}
          fill={INCOME_COLOUR}
          radius={BAR_RADIUS}
        />
        <Bar
          dataKey="expenses"
          name={t('budget.month.expenses')}
          fill={EXPENSE_COLOUR}
          radius={BAR_RADIUS}
        />
      </BarChart>
    </ResponsiveContainer>
  );
}

/** Income vs. expenses of the last six months. */
export function MonthlyTrendCard() {
  const { t } = useTranslation();
  const { data: points, isLoading, isError } = useMonthlyTrend();
  const bars = points ? toTrendBars(points) : [];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('dashboard.monthlyOverview')}</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading && <Skeleton className="h-64 w-full" />}
        {isError && <p className="text-sm text-destructive">{t('budget.month.loadError')}</p>}
        {points &&
          (hasMovement(bars) ? (
            <TrendChart bars={bars} />
          ) : (
            <CardEmptyState
              message={t('dashboard.empty.noTransactions')}
              ctaLabel={t('dashboard.empty.importStatement')}
              to="/budget?tab=month"
            />
          ))}
      </CardContent>
    </Card>
  );
}
