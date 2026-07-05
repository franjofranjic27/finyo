import { useTranslation } from 'react-i18next';
import {
  ComposedChart, Bar, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import type { TaxYearSummary } from '@/api/tax';
import { formatCHF, formatPercent } from '@/lib/formatters';
import { buildComparisonData } from './taxYearUtils';

interface YearComparisonCardProps {
  years: TaxYearSummary[];
  className?: string;
}

export function YearComparisonCard({ years, className }: YearComparisonCardProps) {
  const { t } = useTranslation();

  const data = buildComparisonData(years);
  if (data.length < 2) return null;

  const rateLabel = t('tax.effectiveRate');

  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle className="text-base">{t('tax.comparison')}</CardTitle>
      </CardHeader>
      <CardContent>
        <ResponsiveContainer width="100%" height={280}>
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
            <Legend formatter={(v) => <span style={{ color: 'hsl(var(--foreground))', fontSize: 12 }}>{v}</span>} />
            <Bar yAxisId="chf" dataKey="income" name={t('tax.income')} fill="#c7d2fe" radius={[4, 4, 0, 0]} />
            <Bar yAxisId="chf" dataKey="taxBurden" name={t('tax.taxBurden')} fill="#6366f1" radius={[4, 4, 0, 0]} />
            <Line yAxisId="pct" dataKey="effectiveRate" name={rateLabel} stroke="#10b981" strokeWidth={2} />
          </ComposedChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}
