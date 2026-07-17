import { useTranslation } from 'react-i18next';
import { BarChart, Bar, Cell, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import type { TaxYearDetail } from '@/api/tax';
import { formatCHF } from '@/lib/formatters';
import { aggregatePaymentsByMonth, paymentProgress } from './taxYearUtils';

interface MonthlyPaymentsCardProps {
  detail: TaxYearDetail;
  className?: string;
}

export function MonthlyPaymentsCard({ detail, className }: Readonly<MonthlyPaymentsCardProps>) {
  const { t, i18n } = useTranslation();

  const monthFormatter = new Intl.DateTimeFormat(i18n.language, { month: 'short' });
  const data = aggregatePaymentsByMonth(detail.payments, new Date()).map((bucket) => ({
    ...bucket,
    label: monthFormatter.format(new Date(2000, bucket.month, 1)),
  }));
  const pct = Math.round(paymentProgress(detail.paidTotal, detail.expectedTax));

  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle className="text-base">{t('tax.monthlyPayments')}</CardTitle>
      </CardHeader>
      <CardContent>
        {/* Mobile-only KPI tiles (einzahlungen-monatlich design); desktop stays chart-only. */}
        <div className="mb-4 grid grid-cols-2 gap-3 sm:hidden">
          <div className="rounded-lg border p-3">
            <p className="text-xs text-muted-foreground">{t('tax.paidTotal')}</p>
            <p className="mt-0.5 text-lg font-semibold tabular-nums">
              {formatCHF(detail.paidTotal)}
            </p>
          </div>
          <div className="rounded-lg border p-3">
            <p className="text-xs text-muted-foreground">{t('tax.expectedTaxBurden')}</p>
            <p className="mt-0.5 text-lg font-semibold tabular-nums">
              {detail.expectedTax != null ? formatCHF(detail.expectedTax) : '—'}
            </p>
          </div>
        </div>
        {/* Fixed-height wrapper: compact chart on mobile, unchanged 200 px on sm+. */}
        <div className="h-40 sm:h-[200px]">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data}>
              <XAxis dataKey="label" tick={{ fontSize: 12 }} tickLine={false} axisLine={false} />
              <YAxis hide />
              <Tooltip
                formatter={(v: number) => formatCHF(v)}
                contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))' }}
              />
              <Bar dataKey="amount" radius={[4, 4, 0, 0]}>
                {data.map((bucket) => (
                  <Cell
                    key={bucket.month}
                    fill="#6366f1"
                    fillOpacity={bucket.isPaid ? 1 : 0.25}
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
        <Progress value={pct} className="mt-4" />
        <p className="mt-2 text-sm text-muted-foreground">
          {t('tax.percentPaid', { pct })} · {t('tax.openAmount')}: {formatCHF(detail.openAmount ?? 0)}
        </p>
      </CardContent>
    </Card>
  );
}
