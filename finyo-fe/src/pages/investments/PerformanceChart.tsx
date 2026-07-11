import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import {
  AreaChart, Area, Tooltip, ResponsiveContainer, XAxis, YAxis, CartesianGrid,
} from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { portfolioApi } from '@/api/portfolio';
import { formatCHF, formatDate } from '@/lib/formatters';

type HistoryMonths = 6 | 12 | 36;

const RANGES: ReadonlyArray<{ months: HistoryMonths; labelKey: string }> = [
  { months: 6, labelKey: 'investments.performance.range6m' },
  { months: 12, labelKey: 'investments.performance.range1y' },
  { months: 36, labelKey: 'investments.performance.range3y' },
];

// Module scope: recharts re-renders formatter results, nested components would remount.
const formatTick = (date: string) =>
  new Date(date).toLocaleDateString('de-CH', { day: 'numeric', month: 'numeric' });

export function PerformanceChart() {
  const { t, i18n } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const [months, setMonths] = useState<HistoryMonths>(12);

  const { data, isLoading } = useQuery({
    queryKey: ['portfolio', 'history', months],
    queryFn: () => portfolioApi.getHistory(token, months),
    enabled: !!token,
  });

  const points = data?.points ?? [];

  let content: ReactNode;
  if (isLoading) {
    content = <Skeleton className="h-64 w-full" />;
  } else if (points.length < 2) {
    content = (
      <p className="py-8 text-center text-sm text-muted-foreground">
        {t('investments.performance.notEnoughData')}
      </p>
    );
  } else {
    content = (
      <ResponsiveContainer width="100%" height={260}>
        <AreaChart data={points}>
          <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
          <XAxis
            dataKey="date"
            tickFormatter={formatTick}
            tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v: number) => `${(v / 1000).toFixed(0)}k`}
          />
          <Tooltip
            formatter={(value: number) => formatCHF(value)}
            labelFormatter={(label: string) => formatDate(label, i18n.language)}
            contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))' }}
          />
          <Area type="monotone" dataKey="totalValue" stroke="#10b981" fill="#10b981" fillOpacity={0.6} />
        </AreaChart>
      </ResponsiveContainer>
    );
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0">
        <CardTitle className="text-base">{t('investments.performance.title')}</CardTitle>
        <div className="flex gap-1">
          {RANGES.map((range) => (
            <Button
              key={range.months}
              variant={months === range.months ? 'default' : 'outline'}
              size="sm"
              onClick={() => setMonths(range.months)}
            >
              {t(range.labelKey)}
            </Button>
          ))}
        </div>
      </CardHeader>
      <CardContent>{content}</CardContent>
    </Card>
  );
}
