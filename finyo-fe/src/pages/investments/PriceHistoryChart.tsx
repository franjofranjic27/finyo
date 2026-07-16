import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import {
  AreaChart, Area, Tooltip, ResponsiveContainer, XAxis, YAxis, CartesianGrid,
} from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { positionDetailApi } from '@/api/positionDetail';
import { chartAxisProps, chartGridProps, chartTooltipStyle } from '@/components/charts/chartStyle';
import { formatPrice, formatTick, formatTooltipDate } from './priceFormat';

export function PriceHistoryChart({ positionId }: Readonly<{ positionId: string }>) {
  const { t, i18n } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const { data, isLoading } = useQuery({
    queryKey: ['position', positionId, 'price-history'],
    queryFn: () => positionDetailApi.getPriceHistory(positionId, token),
    enabled: !!token,
  });

  const points = data?.points ?? [];
  const currency = data?.currency ?? null;

  let content: ReactNode;
  if (isLoading) {
    content = <Skeleton className="h-64 w-full" />;
  } else if (points.length === 0) {
    content = (
      <p className="py-8 text-center text-sm text-muted-foreground">
        {t('investments.priceHistory.empty')}
      </p>
    );
  } else {
    content = (
      <ResponsiveContainer width="100%" height={260}>
        <AreaChart data={points}>
          <CartesianGrid {...chartGridProps} />
          <XAxis dataKey="date" tickFormatter={formatTick} {...chartAxisProps} />
          <YAxis
            {...chartAxisProps}
            width={72}
            domain={['auto', 'auto']}
            tickFormatter={(v: number) => formatPrice(v, currency, i18n.language)}
          />
          <Tooltip
            formatter={(value: number) => [
              formatPrice(value, currency, i18n.language),
              t('investments.priceHistory.axisPrice'),
            ]}
            labelFormatter={(label: string) => formatTooltipDate(label, i18n.language)}
            contentStyle={chartTooltipStyle}
          />
          <Area type="monotone" dataKey="close" stroke="#10b981" fill="#10b981" fillOpacity={0.6} />
        </AreaChart>
      </ResponsiveContainer>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('investments.priceHistory.title')}</CardTitle>
      </CardHeader>
      <CardContent>{content}</CardContent>
    </Card>
  );
}
