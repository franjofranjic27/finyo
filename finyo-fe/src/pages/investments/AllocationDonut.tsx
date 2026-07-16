import { useTranslation } from 'react-i18next';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import type { PortfolioPosition } from '@/api/portfolio';
import { formatCHF, formatPercent } from '@/lib/formatters';
import { CHART_COLOURS } from '@/lib/chartColours';
import { displayName } from './positionName';

const CHART_HEIGHT = 240;

export function AllocationDonut({ positions }: Readonly<{ positions: PortfolioPosition[] }>) {
  const { t } = useTranslation();

  // valueChf, not value: the donut is a CHF allocation, so a position is weighted by its converted
  // value. One with no rate yet (valueChf null) has no place in a franc pie and is left out.
  const chartData = positions
    .filter((position) => position.valueChf !== null)
    .map((position) => ({
      id: position.id,
      name: displayName(position),
      value: position.valueChf as number,
    }));

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('investments.allocation.title')}</CardTitle>
      </CardHeader>
      <CardContent>
        {positions.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">
            {t('investments.allocation.empty')}
          </p>
        ) : (
          <div className="flex flex-col gap-4">
            <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
              <PieChart>
                <Pie
                  data={chartData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={100}
                  paddingAngle={3}
                  dataKey="value"
                >
                  {chartData.map((entry, index) => (
                    <Cell key={entry.id} fill={CHART_COLOURS[index % CHART_COLOURS.length]} />
                  ))}
                </Pie>
                <text
                  x="50%"
                  y="50%"
                  dy={-6}
                  textAnchor="middle"
                  dominantBaseline="middle"
                  className="fill-foreground text-2xl font-bold"
                >
                  {positions.length}
                </text>
                <text
                  x="50%"
                  y="50%"
                  dy={16}
                  textAnchor="middle"
                  dominantBaseline="middle"
                  className="fill-muted-foreground text-xs"
                >
                  {t('investments.allocation.positions')}
                </text>
                <Tooltip
                  formatter={(value: number) => formatCHF(value)}
                  contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))' }}
                />
              </PieChart>
            </ResponsiveContainer>

            {/* Left-aligned legend list — truncating names cannot overflow the card. */}
            <ul className="flex w-full flex-col gap-2">
              {positions.map((position, index) => (
                <li key={position.id} className="flex min-w-0 items-center gap-2 text-sm">
                  <span
                    className="h-2.5 w-2.5 shrink-0 rounded-full"
                    style={{ backgroundColor: CHART_COLOURS[index % CHART_COLOURS.length] }}
                    aria-hidden="true"
                  />
                  <span className="min-w-0 truncate">{displayName(position)}</span>
                  <span className="ml-auto shrink-0 tabular-nums text-muted-foreground">
                    {position.allocationPct === null ? '—' : formatPercent(position.allocationPct)}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
