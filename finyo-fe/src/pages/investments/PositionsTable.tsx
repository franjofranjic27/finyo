import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { X } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/auth/useAuth';
import { portfolioApi } from '@/api/portfolio';
import type { PortfolioPosition } from '@/api/portfolio';
import { formatCHF, formatPercent, amountColour } from '@/lib/formatters';
import { CHART_COLOURS } from '@/lib/chartColours';
import { displayName } from './positionName';

function PositionRow({ position, index, onRemove, removing }: Readonly<{
  position: PortfolioPosition;
  index: number;
  onRemove: (position: PortfolioPosition) => void;
  removing: boolean;
}>) {
  const { t } = useTranslation();

  return (
    <tr className="border-b border-border last:border-0">
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
      <td className="py-2 pr-4 text-right tabular-nums">{formatCHF(position.purchasePrice)}</td>
      <td className="py-2 pr-4 text-right tabular-nums">{formatCHF(position.currentPrice)}</td>
      <td className="py-2 pr-4 text-right font-medium tabular-nums">{formatCHF(position.value)}</td>
      <td className={`py-2 pr-4 text-right tabular-nums ${amountColour(position.gainLoss)}`}>
        {formatCHF(position.gainLoss)}
      </td>
      <td className={`py-2 pr-4 text-right tabular-nums ${amountColour(position.returnPct)}`}>
        {formatPercent(position.returnPct)}
      </td>
      <td className="py-2 pr-4 text-right tabular-nums text-muted-foreground">
        {formatPercent(position.allocationPct)}
      </td>
      <td className="py-2 text-right">
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-muted-foreground hover:text-red-500"
          aria-label={t('investments.table.remove')}
          disabled={removing}
          onClick={() => onRemove(position)}
        >
          <X className="h-3.5 w-3.5" />
        </Button>
      </td>
    </tr>
  );
}

export function PositionsTable({ positions }: Readonly<{ positions: PortfolioPosition[] }>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const deletePosition = useMutation({
    mutationFn: (id: string) => portfolioApi.deletePosition(token, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['portfolio'] }),
  });

  const confirmRemove = (position: PortfolioPosition) => {
    if (globalThis.confirm(t('investments.table.confirmRemove', { name: displayName(position) }))) {
      deletePosition.mutate(position.id);
    }
  };

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
                {positions.map((position, index) => (
                  <PositionRow
                    key={position.id}
                    position={position}
                    index={index}
                    onRemove={confirmRemove}
                    removing={deletePosition.isPending}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
