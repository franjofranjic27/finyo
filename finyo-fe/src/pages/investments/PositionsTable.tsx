import { Fragment, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ChevronRight, Pencil, X } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { useAuth } from '@/auth/useAuth';
import { portfolioApi, ASSET_CLASSES } from '@/api/portfolio';
import type { AssetClass, PortfolioPosition } from '@/api/portfolio';
import { formatCHF, formatPercent, formatDate, amountColour } from '@/lib/formatters';
import { formatHolding } from './priceFormat';
import { CHART_COLOURS } from '@/lib/chartColours';
import { displayName } from './positionName';
import { EditPositionDialog } from './EditPositionDialog';
import { PriceSourceBadge } from './PriceSourceBadge';
import { isUnremarkablePrice, usePriceProvenanceText } from './priceProvenance';

interface AssetClassGroup {
  assetClass: AssetClass;
  positions: PortfolioPosition[];
  value: number;
  sharePct: number;
}

/** A dash for a CHF figure a position does not have — its currency has no stored rate yet. */
const NO_VALUE = '—';

/**
 * Groups positions by asset class in display order, dropping empty groups. The group value is in
 * CHF — it sums valueChf, so an unconverted position (no rate yet) contributes nothing rather than
 * adding its foreign face value to a franc total.
 */
function groupByAssetClass(positions: PortfolioPosition[]): AssetClassGroup[] {
  const totalValue = positions.reduce((sum, position) => sum + (position.valueChf ?? 0), 0);
  return ASSET_CLASSES.map((assetClass) => {
    const items = positions.filter((position) => position.assetClass === assetClass);
    const value = items.reduce((sum, position) => sum + (position.valueChf ?? 0), 0);
    return {
      assetClass,
      positions: items,
      value,
      sharePct: totalValue > 0 ? (value / totalValue) * 100 : 0,
    };
  }).filter((group) => group.positions.length > 0);
}

function GroupHeaderRow({ group }: Readonly<{ group: AssetClassGroup }>) {
  const { t } = useTranslation();

  return (
    <tr className="border-b border-border">
      <td colSpan={9} className="pb-1 pt-4">
        <div className="flex items-baseline gap-3">
          <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {t(`assetClass.${group.assetClass}`)}
          </span>
          <span className="text-xs tabular-nums text-muted-foreground">
            {formatCHF(group.value)}
          </span>
          <span className="text-xs tabular-nums text-muted-foreground">
            {formatPercent(group.sharePct)}
          </span>
        </div>
      </td>
    </tr>
  );
}

/**
 * The price plus where it came from. A fresh market price stays unadorned — every
 * other case (stale, hand-typed, or no price at all) is badged, because a number
 * that is not a market price must never look like one.
 */
function PriceCell({ position }: Readonly<{ position: PortfolioPosition }>) {
  const { i18n } = useTranslation();
  const provenance = usePriceProvenanceText(position);

  return (
    <td className="py-2 pr-4">
      <TooltipProvider delayDuration={150}>
        <Tooltip>
          <TooltipTrigger asChild>
            <span className="flex items-center justify-end gap-2" tabIndex={0}>
              {!isUnremarkablePrice(position) && <PriceSourceBadge position={position} />}
              <span className="tabular-nums">{formatHolding(position.currentPrice, position.currency, i18n.language)}</span>
              <span className="sr-only">{provenance}</span>
            </span>
          </TooltipTrigger>
          <TooltipContent>{provenance}</TooltipContent>
        </Tooltip>
      </TooltipProvider>
    </td>
  );
}

/**
 * The position's value in its own currency. When it is a foreign currency that was converted, the
 * cell carries a tooltip with the CHF figure and the exact rate applied — so the number that feeds
 * the CHF total is one the user can reconstruct, not one they have to trust.
 */
function ValueCell({ position }: Readonly<{ position: PortfolioPosition }>) {
  const { t, i18n } = useTranslation();
  const native = formatHolding(position.value, position.currency, i18n.language);
  const converted = position.fxRate !== null && position.valueChf !== null;

  if (!converted) {
    return <td className="py-2 pr-4 text-right font-medium tabular-nums">{native}</td>;
  }

  const rateLabel = position.fxRateType === 'OFFICIAL_CH'
    ? t('investments.table.fxOfficial')
    : t('investments.table.fxMid');
  const parts = [
    formatCHF(position.valueChf as number),
    `${position.currency} @ ${position.fxRate}`,
    rateLabel,
    position.fxRateDate ? formatDate(position.fxRateDate, i18n.language) : null,
  ].filter(Boolean);

  return (
    <td className="py-2 pr-4 text-right font-medium tabular-nums">
      <TooltipProvider delayDuration={150}>
        <Tooltip>
          <TooltipTrigger asChild>
            <span tabIndex={0} className="underline decoration-dotted underline-offset-4">{native}</span>
          </TooltipTrigger>
          <TooltipContent>{parts.join(' · ')}</TooltipContent>
        </Tooltip>
      </TooltipProvider>
    </td>
  );
}

function PositionRow({ position, index, onOpen, onEdit, onRemove, removing }: Readonly<{
  position: PortfolioPosition;
  index: number;
  onOpen: (position: PortfolioPosition) => void;
  onEdit: (position: PortfolioPosition) => void;
  onRemove: (position: PortfolioPosition) => void;
  removing: boolean;
}>) {
  const { t, i18n } = useTranslation();

  return (
    <tr
      className="cursor-pointer border-b border-border last:border-0 hover:bg-secondary/50"
      onClick={() => onOpen(position)}
    >
      <td className="py-2 pr-4">
        <div className="flex items-center gap-2">
          <span
            className="h-2.5 w-2.5 shrink-0 rounded-full"
            style={{ backgroundColor: CHART_COLOURS[index % CHART_COLOURS.length] }}
            aria-hidden="true"
          />
          <div className="min-w-0">
            <p className="truncate font-medium">{displayName(position)}</p>
            {position.isin && (
              <p className="truncate text-xs text-muted-foreground">{position.isin}</p>
            )}
          </div>
        </div>
      </td>
      <td className="py-2 pr-4 text-right tabular-nums">{position.quantity}</td>
      <td className="py-2 pr-4 text-right tabular-nums">
        {formatHolding(position.purchasePrice, position.currency, i18n.language)}
      </td>
      <PriceCell position={position} />
      <ValueCell position={position} />
      <td className={`py-2 pr-4 text-right tabular-nums ${amountColour(position.gainLoss ?? 0)}`}>
        {position.gainLoss === null ? NO_VALUE : formatCHF(position.gainLoss)}
      </td>
      <td className={`py-2 pr-4 text-right tabular-nums ${amountColour(position.returnPct ?? 0)}`}>
        {position.returnPct === null ? NO_VALUE : formatPercent(position.returnPct)}
      </td>
      <td className="py-2 pr-4 text-right tabular-nums text-muted-foreground">
        {position.allocationPct === null ? NO_VALUE : formatPercent(position.allocationPct)}
      </td>
      <td className="py-2 text-right">
        <span className="inline-flex items-center gap-1">
          <ChevronRight className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground hover:text-foreground"
            aria-label={t('investments.table.edit')}
            onClick={(event) => {
              event.stopPropagation();
              onEdit(position);
            }}
          >
            <Pencil className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground hover:text-red-500"
            aria-label={t('investments.table.remove')}
            disabled={removing}
            onClick={(event) => {
              event.stopPropagation();
              onRemove(position);
            }}
          >
            <X className="h-3.5 w-3.5" />
          </Button>
        </span>
      </td>
    </tr>
  );
}

export function PositionsTable({ positions }: Readonly<{ positions: PortfolioPosition[] }>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [editing, setEditing] = useState<PortfolioPosition | null>(null);

  const deletePosition = useMutation({
    mutationFn: (id: string) => portfolioApi.deletePosition(token, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['portfolio'] }),
  });

  const confirmRemove = (position: PortfolioPosition) => {
    if (globalThis.confirm(t('investments.table.confirmRemove', { name: displayName(position) }))) {
      deletePosition.mutate(position.id);
    }
  };

  const openDetail = (position: PortfolioPosition) => {
    navigate(`/investments/positions/${position.positionId}`);
  };

  const groups = groupByAssetClass(positions);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('investments.table.title')}</CardTitle>
      </CardHeader>
      <CardContent>
        {positions.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">
            {t('investments.table.empty')}
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs text-muted-foreground">
                  <th className="py-2 pr-4 font-medium">{t('investments.table.security')}</th>
                  <th className="py-2 pr-4 text-right font-medium">{t('investments.table.quantity')}</th>
                  <th className="py-2 pr-4 text-right font-medium">{t('investments.table.purchasePrice')}</th>
                  <th className="py-2 pr-4 text-right font-medium">{t('investments.table.currentPrice')}</th>
                  <th className="py-2 pr-4 text-right font-medium">{t('investments.table.value')}</th>
                  <th className="py-2 pr-4 text-right font-medium">{t('investments.table.gainLoss')}</th>
                  <th className="py-2 pr-4 text-right font-medium">{t('investments.table.returnPct')}</th>
                  <th className="py-2 pr-4 text-right font-medium">{t('investments.table.allocation')}</th>
                  <th className="py-2" aria-hidden="true" />
                </tr>
              </thead>
              <tbody>
                {groups.map((group) => (
                  <Fragment key={group.assetClass}>
                    <GroupHeaderRow group={group} />
                    {group.positions.map((position) => (
                      <PositionRow
                        key={position.id}
                        position={position}
                        // Colour index from the original array keeps the dot in
                        // sync with the allocation donut slice colours.
                        index={positions.indexOf(position)}
                        onOpen={openDetail}
                        onEdit={setEditing}
                        onRemove={confirmRemove}
                        removing={deletePosition.isPending}
                      />
                    ))}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {editing && (
          <EditPositionDialog
            positionId={editing.positionId}
            initial={{
              quantity: editing.quantity,
              purchasePrice: editing.purchasePrice,
              purchaseDate: editing.purchaseDate,
              currentPrice: editing.currentPrice,
            }}
            onClose={() => setEditing(null)}
          />
        )}
      </CardContent>
    </Card>
  );
}
