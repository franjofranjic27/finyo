import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts';
import { ArrowLeft } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import type { AssetClass, PortfolioPosition } from '@/api/portfolio';
import { formatCHF, formatPercent } from '@/lib/formatters';
import {
  chartTooltipItemStyle,
  chartTooltipStyle,
  formatTooltipCHF,
} from '@/components/charts/chartStyle';
import { CHART_COLOURS } from '@/lib/chartColours';
import { displayName } from './positionName';
import { groupByAssetClass } from './assetClassGroups';
import type { AssetClassGroup } from './assetClassGroups';

const CHART_HEIGHT = 240;

/**
 * One fixed colour per asset class, assigned in ASSET_CLASSES display order. Unlike the
 * per-position palette (cycled by index), a class keeps its colour regardless of which
 * classes are present — ETF is always indigo, BOND always the lightest tint.
 */
const ASSET_CLASS_COLOURS: Readonly<Record<AssetClass, string>> = {
  ETF: CHART_COLOURS[0],
  FUND: CHART_COLOURS[1],
  STOCK: CHART_COLOURS[2],
  CRYPTO: CHART_COLOURS[3],
  BOND: CHART_COLOURS[4],
};

type DonutView =
  | { readonly mode: 'groups' }
  | { readonly mode: 'positions' }
  | { readonly mode: 'drilldown'; readonly assetClass: AssetClass };

interface DonutSlice {
  readonly id: string;
  readonly name: string;
  readonly value: number;
  readonly colour: string;
}

function DonutChart({ slices, centreCount, centreLabel, onSliceClick }: Readonly<{
  slices: DonutSlice[];
  centreCount: number;
  centreLabel: string;
  onSliceClick?: (index: number) => void;
}>) {
  return (
    <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
      <PieChart>
        <Pie
          data={slices}
          cx="50%"
          cy="50%"
          innerRadius={60}
          outerRadius={100}
          paddingAngle={3}
          dataKey="value"
          className={onSliceClick ? 'cursor-pointer' : undefined}
          onClick={onSliceClick ? (_, index) => onSliceClick(index) : undefined}
        >
          {slices.map((slice) => (
            <Cell key={slice.id} fill={slice.colour} />
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
          {centreCount}
        </text>
        <text
          x="50%"
          y="50%"
          dy={16}
          textAnchor="middle"
          dominantBaseline="middle"
          className="fill-muted-foreground text-xs"
        >
          {centreLabel}
        </text>
        <Tooltip
          formatter={formatTooltipCHF}
          contentStyle={chartTooltipStyle}
          itemStyle={chartTooltipItemStyle}
        />
      </PieChart>
    </ResponsiveContainer>
  );
}

function LegendDot({ colour }: Readonly<{ colour: string }>) {
  return (
    <span
      className="h-2.5 w-2.5 shrink-0 rounded-full"
      style={{ backgroundColor: colour }}
      aria-hidden="true"
    />
  );
}

/** Legend rows are buttons, so a group is drillable by keyboard, not only via its pie slice. */
function GroupLegend({ groups, onSelect }: Readonly<{
  groups: AssetClassGroup[];
  onSelect: (assetClass: AssetClass) => void;
}>) {
  const { t } = useTranslation();

  return (
    <ul className="flex w-full flex-col gap-1">
      {groups.map((group) => (
        <li key={group.assetClass} data-testid="donut-legend-item" className="min-w-0 text-sm">
          <button
            type="button"
            className="flex w-full min-w-0 items-center gap-2 rounded-md px-1 py-1 text-left hover:bg-secondary/50"
            onClick={() => onSelect(group.assetClass)}
          >
            <LegendDot colour={ASSET_CLASS_COLOURS[group.assetClass]} />
            <span className="min-w-0 truncate">{t(`assetClass.${group.assetClass}`)}</span>
            <span className="ml-auto shrink-0 tabular-nums text-muted-foreground">
              {formatCHF(group.value)} · {formatPercent(group.sharePct)}
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}

/**
 * Legend for position views. `allPositions` provides the colour index: a position keeps
 * the colour of its place in the full list (the PositionsTable dot contract), so a
 * drilled-down subset shows the same colours as the table and the all-positions donut.
 */
function PositionLegend({ shown, allPositions }: Readonly<{
  shown: PortfolioPosition[];
  allPositions: PortfolioPosition[];
}>) {
  return (
    <ul className="flex w-full flex-col gap-2">
      {shown.map((position) => (
        <li
          key={position.id}
          data-testid="donut-legend-item"
          className="flex min-w-0 items-center gap-2 text-sm"
        >
          <LegendDot
            colour={CHART_COLOURS[allPositions.indexOf(position) % CHART_COLOURS.length]}
          />
          <span className="min-w-0 truncate">{displayName(position)}</span>
          <span className="ml-auto shrink-0 tabular-nums text-muted-foreground">
            {position.allocationPct === null ? '—' : formatPercent(position.allocationPct)}
          </span>
        </li>
      ))}
    </ul>
  );
}

export function AllocationDonut({ positions }: Readonly<{ positions: PortfolioPosition[] }>) {
  const { t } = useTranslation();
  const [view, setView] = useState<DonutView>({ mode: 'groups' });

  const groups = groupByAssetClass(positions);
  const drilledGroup = view.mode === 'drilldown'
    ? groups.find((group) => group.assetClass === view.assetClass)
    : undefined;
  // A drilldown into a class whose last position vanished falls back to the groups view.
  const mode = view.mode === 'drilldown' && !drilledGroup ? 'groups' : view.mode;

  const drillInto = (assetClass: AssetClass) => setView({ mode: 'drilldown', assetClass });

  // valueChf, not value: the donut is a CHF allocation, so a position is weighted by its converted
  // value. One with no rate yet (valueChf null) has no place in a franc pie and is left out.
  const positionSlices = (shown: PortfolioPosition[]): DonutSlice[] =>
    shown
      .filter((position) => position.valueChf !== null)
      .map((position) => ({
        id: position.id,
        name: displayName(position),
        value: position.valueChf as number,
        colour: CHART_COLOURS[positions.indexOf(position) % CHART_COLOURS.length],
      }));

  const groupSlices: DonutSlice[] = groups.map((group) => ({
    id: group.assetClass,
    name: t(`assetClass.${group.assetClass}`),
    value: group.value,
    colour: ASSET_CLASS_COLOURS[group.assetClass],
  }));

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0">
        <CardTitle className="text-base">{t('investments.allocation.title')}</CardTitle>
        {/* Drilldown swaps the chip row for the back button — one control zone, no crowding
            on phone widths. Chips scroll horizontally instead of squeezing. */}
        {mode === 'drilldown' && drilledGroup ? (
          <Button
            variant="ghost"
            size="sm"
            className="shrink-0"
            aria-label={t('investments.allocation.backToGroups')}
            onClick={() => setView({ mode: 'groups' })}
          >
            <ArrowLeft aria-hidden="true" />
            {t(`assetClass.${drilledGroup.assetClass}`)}
          </Button>
        ) : (
          positions.length > 0 && (
            <div className="flex min-w-0 gap-1 overflow-x-auto">
              <Button
                variant={mode === 'groups' ? 'default' : 'outline'}
                size="sm"
                className="shrink-0"
                onClick={() => setView({ mode: 'groups' })}
              >
                {t('investments.allocation.viewGroups')}
              </Button>
              <Button
                variant={mode === 'positions' ? 'default' : 'outline'}
                size="sm"
                className="shrink-0"
                onClick={() => setView({ mode: 'positions' })}
              >
                {t('investments.allocation.viewPositions')}
              </Button>
            </div>
          )
        )}
      </CardHeader>
      <CardContent>
        {positions.length === 0 && (
          <p className="py-8 text-center text-sm text-muted-foreground">
            {t('investments.allocation.empty')}
          </p>
        )}
        {positions.length > 0 && mode === 'groups' && (
          <div className="flex flex-col gap-4">
            <DonutChart
              slices={groupSlices}
              centreCount={groups.length}
              centreLabel={t('investments.allocation.groups')}
              onSliceClick={(index) => drillInto(groups[index].assetClass)}
            />
            <GroupLegend groups={groups} onSelect={drillInto} />
          </div>
        )}
        {positions.length > 0 && mode === 'positions' && (
          <div className="flex flex-col gap-4">
            <DonutChart
              slices={positionSlices(positions)}
              centreCount={positions.length}
              centreLabel={t('investments.allocation.positions')}
            />
            <PositionLegend shown={positions} allPositions={positions} />
          </div>
        )}
        {mode === 'drilldown' && drilledGroup && (
          <div className="flex flex-col gap-4">
            <DonutChart
              slices={positionSlices(drilledGroup.positions)}
              centreCount={drilledGroup.positions.length}
              centreLabel={t('investments.allocation.positions')}
            />
            <PositionLegend shown={drilledGroup.positions} allPositions={positions} />
          </div>
        )}
      </CardContent>
    </Card>
  );
}
