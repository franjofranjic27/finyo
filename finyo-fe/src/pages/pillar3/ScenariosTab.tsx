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
import {
  chartAxisProps,
  chartGridProps,
  chartTooltipStyle,
  formatThousandsTick,
  formatTooltipCHF,
  renderLegendText,
} from '@/components/charts/chartStyle';
import { productColor } from './productColors';
import { ScenarioActionsMenu } from './ScenarioActionsMenu';

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

  const items = useMemo(() => scenarios ?? [], [scenarios]);
  const chartData = useMemo(() => mergeProjections(items), [items]);

  if (isLoading) {
    return <Skeleton className="h-64 w-full" />;
  }

  if (items.length === 0) {
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
              {items.map((scenario, index) => (
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
              <CartesianGrid {...chartGridProps} />
              <XAxis dataKey="year" {...chartAxisProps} />
              <YAxis {...chartAxisProps} tickFormatter={formatThousandsTick} />
              <Tooltip formatter={formatTooltipCHF} contentStyle={chartTooltipStyle} />
              <Legend formatter={renderLegendText} />
              {items.map((scenario, index) => (
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
