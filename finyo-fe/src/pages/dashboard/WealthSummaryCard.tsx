import { useTranslation } from 'react-i18next';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { CardEmptyState } from '@/components/CardEmptyState';
import {
  chartTooltipStyle,
  formatTooltipCHF,
} from '@/components/charts/chartStyle';
import type { WealthBucket, WealthOverview } from '@/api/wealth';
import { useWealthOverview } from '@/hooks/financeQueries';
import { amountColour, formatCHF, formatPercent } from '@/lib/formatters';
import { CHART_COLOURS } from '@/lib/chartColours';

const CHART_HEIGHT = 220;

function bucketColour(index: number): string {
  return CHART_COLOURS[index % CHART_COLOURS.length];
}

/** Prefixes gains with "+" so they read as "+CHF 1'000.00". */
function signed(value: number, formatted: string): string {
  return value > 0 ? `+${formatted}` : formatted;
}

function YtdChip({ change, changePct }: Readonly<{ change: number; changePct: number }>) {
  const { t } = useTranslation();

  return (
    <span
      className={`rounded-full bg-muted px-2.5 py-1 text-xs font-medium tabular-nums ${amountColour(change)}`}
    >
      {signed(change, formatCHF(change))} ({signed(changePct, formatPercent(changePct))}){' '}
      <span className="text-muted-foreground">{t('dashboard.ytd')}</span>
    </span>
  );
}

function AllocationChart({ buckets }: Readonly<{ buckets: WealthBucket[] }>) {
  const { t } = useTranslation();
  const chartData = buckets.map((bucket) => ({
    id: bucket.id,
    name: bucket.name,
    value: bucket.balance,
  }));

  return (
    <div className="grid items-center gap-4 sm:grid-cols-2">
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <PieChart>
          <Pie
            data={chartData}
            cx="50%"
            cy="50%"
            innerRadius={55}
            outerRadius={95}
            paddingAngle={3}
            dataKey="value"
          >
            {chartData.map((entry, index) => (
              <Cell key={entry.id} fill={bucketColour(index)} />
            ))}
          </Pie>
          <Tooltip formatter={formatTooltipCHF} contentStyle={chartTooltipStyle} />
        </PieChart>
      </ResponsiveContainer>

      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          {t('dashboard.allocation')}
        </p>
        <ul className="flex flex-col gap-2">
          {buckets.map((bucket, index) => (
            <li
              key={bucket.id}
              data-testid="donut-legend-item"
              className="flex min-w-0 items-center gap-2 text-sm"
            >
              <span
                className="h-2.5 w-2.5 shrink-0 rounded-full"
                style={{ backgroundColor: bucketColour(index) }}
                aria-hidden="true"
              />
              <span className="min-w-0 truncate">{bucket.name}</span>
              <span className="ml-auto shrink-0 tabular-nums text-muted-foreground">
                {formatPercent(bucket.sharePct)}
              </span>
              <span className="w-28 shrink-0 text-right font-medium tabular-nums">
                {formatCHF(bucket.balance)}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

function WealthSummaryContent({ overview }: Readonly<{ overview: WealthOverview }>) {
  const { t } = useTranslation();
  const { ytdChange, ytdChangePct } = overview;

  if (overview.buckets.length === 0) {
    return (
      <CardEmptyState
        message={t('dashboard.empty.noWealth')}
        ctaLabel={t('dashboard.empty.createBuckets')}
        to="/wealth"
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <p className="text-3xl font-bold tabular-nums">{formatCHF(overview.total)}</p>
        {ytdChange !== null && ytdChangePct !== null && (
          <YtdChip change={ytdChange} changePct={ytdChangePct} />
        )}
      </div>
      <AllocationChart buckets={overview.buckets} />
    </div>
  );
}

/** Total wealth and its allocation across the wealth buckets. */
export function WealthSummaryCard() {
  const { t } = useTranslation();
  const { data: overview, isLoading, isError } = useWealthOverview();

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('dashboard.wealthTotal')}</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading && <Skeleton className="h-64 w-full" />}
        {isError && <p className="text-sm text-destructive">{t('wealth.loadError')}</p>}
        {overview && <WealthSummaryContent overview={overview} />}
      </CardContent>
    </Card>
  );
}
