import { useTranslation } from 'react-i18next';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { CardEmptyState } from '@/components/CardEmptyState';
import { chartTooltipStyle, formatTooltipCHF } from '@/components/charts/chartStyle';
import type { RangeCategoryBreakdown } from '@/api/analytics';
import { useMonthCategoryBreakdown } from '@/hooks/financeQueries';
import { CHART_COLOURS } from '@/lib/chartColours';
import { formatCHF, formatPercent } from '@/lib/formatters';
import { categoryAmount, categoryLabel } from './categoryBreakdown';

const CHART_HEIGHT = 240;
const MAX_SLICES = 6;
const OTHER_SLICE_ID = 'other';
/** Neutral grey so the aggregate slice does not read as another category. */
const OTHER_COLOUR = '#94a3b8';

interface Slice {
  id: string;
  name: string;
  value: number;
  percentage: number;
  colour: string;
}

interface SliceLabels {
  uncategorized: string;
  other: string;
}

function toSlice(item: RangeCategoryBreakdown, index: number, uncategorized: string): Slice {
  return {
    id: item.categoryId ?? 'uncategorized',
    name: categoryLabel(item, uncategorized),
    value: categoryAmount(item),
    percentage: item.percentage,
    colour: item.categoryColor ?? CHART_COLOURS[index % CHART_COLOURS.length],
  };
}

/** Sums the categories beyond the cut-off so the slices always add up to 100 %. */
function toOtherSlice(rest: readonly RangeCategoryBreakdown[], name: string): Slice {
  return {
    id: OTHER_SLICE_ID,
    name,
    value: rest.reduce((sum, item) => sum + categoryAmount(item), 0),
    percentage: rest.reduce((sum, item) => sum + item.percentage, 0),
    colour: OTHER_COLOUR,
  };
}

function toSlices(items: readonly RangeCategoryBreakdown[], labels: SliceLabels): Slice[] {
  const top = items
    .slice(0, MAX_SLICES)
    .map((item, index) => toSlice(item, index, labels.uncategorized));
  const rest = items.slice(MAX_SLICES);

  return rest.length === 0 ? top : [...top, toOtherSlice(rest, labels.other)];
}

function SpendingDonut({ slices }: Readonly<{ slices: Slice[] }>) {
  return (
    <div className="flex flex-col gap-4">
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <PieChart>
          <Pie
            data={slices}
            cx="50%"
            cy="50%"
            innerRadius={55}
            outerRadius={95}
            paddingAngle={3}
            dataKey="value"
          >
            {slices.map((slice) => (
              <Cell key={slice.id} fill={slice.colour} />
            ))}
          </Pie>
          <Tooltip formatter={formatTooltipCHF} contentStyle={chartTooltipStyle} />
        </PieChart>
      </ResponsiveContainer>

      {/* Left-aligned legend list — long category names truncate instead of overflowing. */}
      <ul className="flex w-full flex-col gap-2">
        {slices.map((slice) => (
          <li
            key={slice.id}
            data-testid="donut-legend-item"
            className="flex min-w-0 items-center gap-2 text-sm"
          >
            <span
              className="h-2.5 w-2.5 shrink-0 rounded-full"
              style={{ backgroundColor: slice.colour }}
              aria-hidden="true"
            />
            <span className="min-w-0 truncate">{slice.name}</span>
            <span className="ml-auto shrink-0 tabular-nums text-muted-foreground">
              {formatPercent(slice.percentage)}
            </span>
            <span className="w-28 shrink-0 text-right font-medium tabular-nums">
              {formatCHF(slice.value)}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Spending of the running month split by category, including uncategorised. */
export function SpendingDonutCard() {
  const { t } = useTranslation();
  const { data: items, isLoading, isError } = useMonthCategoryBreakdown();

  const labels: SliceLabels = {
    uncategorized: t('budget.month.uncategorized'),
    other: t('dashboard.otherCategories'),
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('dashboard.spendingByCategory')}</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading && <Skeleton className="h-64 w-full" />}
        {isError && <p className="text-sm text-destructive">{t('dashboard.loadError')}</p>}
        {items &&
          (items.length === 0 ? (
            // An income-only month has transactions but no spending — say so.
            <CardEmptyState
              message={t('dashboard.empty.noSpending')}
              ctaLabel={t('dashboard.empty.importStatement')}
              to="/budget?tab=month"
            />
          ) : (
            <SpendingDonut slices={toSlices(items, labels)} />
          ))}
      </CardContent>
    </Card>
  );
}
