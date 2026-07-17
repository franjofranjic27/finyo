import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import {
  ComposedChart, Area, Line, Legend, Tooltip, ResponsiveContainer, XAxis, YAxis, CartesianGrid,
} from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { wealthApi } from '@/api/wealth';
import type { WealthOverview } from '@/api/wealth';
import { formatCHF, formatDate } from '@/lib/formatters';

const HISTORY_MONTHS = 12;
const LINE_COLOUR = '#6366f1';

interface ChartPoint {
  date: string;
  history?: number;
  forecast?: number;
}

/** Continues today's total with the flat monthly savings rate until December. */
function buildForecast(overview: WealthOverview): ChartPoint[] {
  const start = new Date(overview.asOf);
  // Date-only anchor so it shares the category of today's history snapshot.
  const points: ChartPoint[] = [{ date: overview.asOf.slice(0, 10), forecast: overview.total }];
  for (let month = start.getMonth() + 1; month <= 11; month++) {
    points.push({
      date: new Date(Date.UTC(start.getFullYear(), month, 1)).toISOString().slice(0, 10),
      forecast: overview.total + overview.totalMonthlyRate * (month - start.getMonth()),
    });
  }
  return points;
}

/** Merges both series into one point per date so history and forecast connect visually. */
function mergeSeries(history: ChartPoint[], forecast: ChartPoint[]): ChartPoint[] {
  const byDate = new Map<string, ChartPoint>(history.map((point) => [point.date, { ...point }]));
  for (const point of forecast) {
    const existing = byDate.get(point.date);
    if (existing) {
      existing.forecast = point.forecast;
    } else {
      byDate.set(point.date, point);
    }
  }
  return [...byDate.values()];
}

export function WealthChart({ overview }: Readonly<{ overview: WealthOverview }>) {
  const { t, i18n } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const { data, isLoading } = useQuery({
    queryKey: ['wealth', 'history'],
    queryFn: () => wealthApi.getHistory(token, HISTORY_MONTHS),
    enabled: !!token,
  });

  const historyPoints = data?.points ?? [];

  const formatTick = (date: string) =>
    new Date(date).toLocaleDateString(i18n.language === 'de' ? 'de-CH' : 'en-GB', {
      day: 'numeric',
      month: 'numeric',
    });

  let content: ReactNode;
  if (isLoading) {
    content = <Skeleton className="h-64 w-full" />;
  } else if (historyPoints.length < 2) {
    content = (
      <p className="py-8 text-center text-sm text-muted-foreground">{t('wealth.chart.empty')}</p>
    );
  } else {
    const chartData = mergeSeries(
      historyPoints.map((point) => ({ date: point.date, history: point.total })),
      buildForecast(overview),
    );
    content = (
      // Compact chart height on mobile, unchanged 260px from lg up.
      <div className="h-52 lg:h-[260px]">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={chartData}>
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
            <Legend wrapperStyle={{ fontSize: 12 }} />
            <Area
              type="monotone"
              dataKey="history"
              name={t('wealth.chart.history')}
              stroke={LINE_COLOUR}
              strokeWidth={2}
              fill={LINE_COLOUR}
              fillOpacity={0.15}
            />
            <Line
              type="monotone"
              dataKey="forecast"
              name={t('wealth.chart.forecast')}
              stroke={LINE_COLOUR}
              strokeWidth={2}
              strokeDasharray="4 5"
              dot={false}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('wealth.chart.title')}</CardTitle>
      </CardHeader>
      <CardContent>{content}</CardContent>
    </Card>
  );
}
