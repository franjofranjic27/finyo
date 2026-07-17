import { useTranslation } from 'react-i18next';
import {
  ComposedChart, Bar, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import type { TaxYearSummary } from '@/api/tax';
import { formatCHF, formatPercent } from '@/lib/formatters';
import { buildComparisonData } from './taxYearUtils';

// Module scope: recharts re-renders formatter results, nested components would remount.
const renderLegendText = (value: string) => (
  <span style={{ color: 'hsl(var(--foreground))', fontSize: 12 }}>{value}</span>
);

interface YearComparisonCardProps {
  years: TaxYearSummary[];
  className?: string;
}

export function YearComparisonCard({ years, className }: Readonly<YearComparisonCardProps>) {
  const { t } = useTranslation();

  const data = buildComparisonData(years);
  if (data.length < 2) return null;

  const rateLabel = t('tax.effectiveRate');

  // KPI tiles (mobile only, jahresvergleich design): average burden and
  // the latest year-over-year delta, both derived from the chart data.
  const averageBurden =
    data.reduce((sum, datum) => sum + datum.taxBurden, 0) / data.length;
  const latest = data[data.length - 1];
  const previous = data[data.length - 2];
  const delta = latest.taxBurden - previous.taxBurden;
  const deltaPercent = previous.taxBurden > 0 ? (delta / previous.taxBurden) * 100 : null;

  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle className="text-base">{t('tax.comparison')}</CardTitle>
      </CardHeader>
      <CardContent>
        {/* Fixed-height wrapper: compact chart on mobile, unchanged 280 px on sm+. */}
        <div className="h-56 sm:h-[280px]">
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={data}>
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis dataKey="year" tick={{ fontSize: 12 }} />
              <YAxis
                yAxisId="chf"
                tick={{ fontSize: 12 }}
                tickFormatter={(v: number) => `${Math.round(v / 1000)}k`}
              />
              <YAxis
                yAxisId="pct"
                orientation="right"
                tick={{ fontSize: 12 }}
                tickFormatter={(v: number) => `${v}%`}
              />
              <Tooltip
                formatter={(value: number, name: string) =>
                  name === rateLabel ? formatPercent(value) : formatCHF(value)
                }
                contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))' }}
              />
              <Legend formatter={renderLegendText} />
              <Bar yAxisId="chf" dataKey="income" name={t('tax.income')} fill="#c7d2fe" radius={[4, 4, 0, 0]} />
              <Bar yAxisId="chf" dataKey="taxBurden" name={t('tax.taxBurden')} fill="#6366f1" radius={[4, 4, 0, 0]} />
              <Line yAxisId="pct" dataKey="effectiveRate" name={rateLabel} stroke="#10b981" strokeWidth={2} />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
        {/* Mobile-only KPI tiles; the desktop card stays chart-only. */}
        <div className="mt-4 grid grid-cols-2 gap-3 sm:hidden">
          <div className="rounded-lg border p-3">
            <p className="text-xs text-muted-foreground">
              {t('tax.avgTaxBurden', { from: data[0].year, to: latest.year })}
            </p>
            <p className="mt-0.5 text-lg font-semibold tabular-nums">
              {formatCHF(averageBurden)}
            </p>
          </div>
          <div className="rounded-lg border p-3">
            <p className="text-xs text-muted-foreground">
              {t('tax.vsPreviousYear', { year: latest.year, previous: previous.year })}
            </p>
            <p className="mt-0.5 text-lg font-semibold tabular-nums">
              {delta >= 0 ? '+' : '−'} {formatCHF(Math.abs(delta))}
            </p>
            {deltaPercent != null && (
              <p
                className={
                  delta >= 0
                    ? 'text-xs tabular-nums text-rose-600'
                    : 'text-xs tabular-nums text-emerald-500'
                }
              >
                {delta >= 0 ? '+' : '−'}{formatPercent(Math.abs(deltaPercent))}
              </p>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
