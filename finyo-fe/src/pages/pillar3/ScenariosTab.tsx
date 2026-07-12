import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import {
  CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { BookmarkPlus } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { pillar3Api } from '@/api/pillar3';
import type { Pillar3Scenario } from '@/api/pillar3';
import { formatCHF } from '@/lib/formatters';
import { productColor } from './productColors';
import { ScenarioActionsMenu } from './ScenarioActionsMenu';

// Module scope: recharts re-renders formatter results, nested components would remount.
const renderLegendText = (value: string) => (
  <span style={{ color: 'hsl(var(--foreground))', fontSize: 12 }}>{value}</span>
);

/** One chart row per calendar year: shorter horizons simply end (no connectNulls). */
function mergeProjections(
  scenarios: readonly Pillar3Scenario[],
): Array<Record<string, number>> {
  const byYear = new Map<number, Record<string, number>>();
  for (const scenario of scenarios) {
    for (const point of scenario.calculation.yearlyProjection) {
      const row = byYear.get(point.year) ?? { year: point.year };
      row[scenario.id] = Math.round(point.balance);
      byYear.set(point.year, row);
    }
  }
  return [...byYear.values()].sort((a, b) => a.year - b.year);
}

export function ScenariosTab() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const { data: scenarios, isLoading } = useQuery({
    queryKey: ['pillar3-scenarios'],
    queryFn: () => pillar3Api.getScenarios(token),
    enabled: !!token,
  });

  const chartData = useMemo(() => mergeProjections(scenarios ?? []), [scenarios]);

  if (isLoading) {
    return <Skeleton className="h-64 w-full" />;
  }

  if ((scenarios ?? []).length === 0) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
          <BookmarkPlus className="h-8 w-8 text-muted-foreground" />
          <p className="max-w-md text-sm text-muted-foreground">
            {t('pillar3.scenarios.empty')}
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('pillar3.scenarios.title')}</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('pillar3.scenarios.colName')}</TableHead>
                <TableHead>{t('pillar3.scenarios.colProduct')}</TableHead>
                <TableHead className="text-right">{t('pillar3.scenarios.colContribution')}</TableHead>
                <TableHead className="text-right">{t('pillar3.scenarios.colFinalBalance')}</TableHead>
                <TableHead className="text-right">{t('pillar3.scenarios.colTaxSaving')}</TableHead>
                <TableHead className="text-right">{t('pillar3.scenarios.colNetAfterTax')}</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {(scenarios ?? []).map((scenario, index) => (
                <TableRow key={scenario.id}>
                  <TableCell>
                    <div className="flex items-center gap-3">
                      <span
                        className="h-3 w-3 shrink-0 rounded-full"
                        style={{ backgroundColor: productColor(index) }}
                        aria-hidden
                      />
                      <span className="inline-flex items-center gap-2 font-medium">
                        {scenario.name}
                        {scenario.isDefault && (
                          <Badge variant="secondary">
                            ★ {t('pillar3.scenarios.default')}
                          </Badge>
                        )}
                      </span>
                    </div>
                  </TableCell>
                  <TableCell>
                    {scenario.product ? (
                      <div className="min-w-0">
                        <p className="truncate font-medium">{scenario.product.name}</p>
                        <p className="truncate text-xs text-muted-foreground">
                          {scenario.product.provider}
                        </p>
                      </div>
                    ) : (
                      <div>
                        <p className="font-medium tabular-nums">
                          {scenario.effectiveReturnPercent.toFixed(1)} %
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {t('pillar3.scenarios.manualReturn')}
                        </p>
                      </div>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    {formatCHF(scenario.inputs.annualContribution)}
                  </TableCell>
                  <TableCell className="text-right font-semibold">
                    {formatCHF(scenario.calculation.projectedBalanceAtRetirement)}
                  </TableCell>
                  <TableCell className="text-right">
                    {scenario.calculation.annualTaxSaving > 0
                      ? formatCHF(scenario.calculation.annualTaxSaving)
                      : '–'}
                  </TableCell>
                  <TableCell className="text-right">
                    {formatCHF(scenario.calculation.netValueAfterPayoutTax)}
                  </TableCell>
                  <TableCell className="w-10 text-right">
                    <ScenarioActionsMenu scenario={scenario} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('pillar3.scenarios.chartTitle')}</CardTitle>
        </CardHeader>
        <CardContent>
          <ResponsiveContainer width="100%" height={320}>
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis dataKey="year" tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }} axisLine={false} tickLine={false} />
              <YAxis
                tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }}
                axisLine={false}
                tickLine={false}
                tickFormatter={(v: number) => `${(v / 1000).toFixed(0)}k`}
              />
              <Tooltip
                formatter={(v: number) => formatCHF(v)}
                contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))' }}
              />
              <Legend formatter={renderLegendText} />
              {(scenarios ?? []).map((scenario, index) => (
                <Line
                  key={scenario.id}
                  type="monotone"
                  dataKey={scenario.id}
                  name={scenario.name}
                  dot={false}
                  strokeWidth={scenario.isDefault ? 3 : 1.5}
                  stroke={productColor(index)}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>
    </div>
  );
}
