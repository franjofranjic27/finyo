import { useTranslation } from 'react-i18next';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import type { PortfolioPosition } from '@/api/portfolio';
import { formatCHF, formatPercent } from '@/lib/formatters';
import { CHART_COLOURS } from '@/lib/chartColours';
import { displayName } from './positionName';

const CHART_HEIGHT = 260;
const LEGEND_HEIGHT = 36;
// Vertical centre of the plot area (chart height minus the bottom legend).
const DONUT_CENTRE_Y = (CHART_HEIGHT - LEGEND_HEIGHT) / 2;

// Module scope: recharts re-renders formatter results, nested components would remount.
const renderLegendText = (value: string) => (
  <span style={{ color: 'hsl(var(--foreground))', fontSize: 12 }}>{value}</span>
);

export function AllocationDonut({ positions }: Readonly<{ positions: PortfolioPosition[] }>) {
  const { t } = useTranslation();

  const chartData = positions.map((position) => ({
    id: position.id,
    name: `${displayName(position)} · ${formatPercent(position.allocationPct)}`,
    value: position.value,
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
          <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
            <PieChart>
              <Pie
                data={chartData}
                cx="50%"
                cy={DONUT_CENTRE_Y}
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
                y={DONUT_CENTRE_Y - 6}
                textAnchor="middle"
                dominantBaseline="middle"
                className="fill-foreground text-2xl font-bold"
              >
                {positions.length}
              </text>
              <text
                x="50%"
                y={DONUT_CENTRE_Y + 16}
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
              <Legend formatter={renderLegendText} height={LEGEND_HEIGHT} />
            </PieChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  );
}
