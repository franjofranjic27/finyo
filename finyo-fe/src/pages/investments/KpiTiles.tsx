import { useTranslation } from 'react-i18next';
import { Card, CardContent } from '@/components/ui/card';
import { formatCHF, formatPercent, amountColour } from '@/lib/formatters';
import type { Portfolio } from '@/api/portfolio';

/** Prefixes positive amounts with "+" so gains read as "+CHF 1'000.00". */
function signed(value: number, formatted: string): string {
  return value > 0 ? `+${formatted}` : formatted;
}

function KpiTile({ label, value, valueClass }: Readonly<{
  label: string;
  value: string;
  valueClass?: string;
}>) {
  return (
    <Card>
      <CardContent className="pt-4">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className={`mt-1 text-xl font-bold tabular-nums ${valueClass ?? 'text-foreground'}`}>
          {value}
        </p>
      </CardContent>
    </Card>
  );
}

export function KpiTiles({ portfolio }: Readonly<{ portfolio: Portfolio }>) {
  const { t } = useTranslation();

  return (
    <div className="space-y-2">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <KpiTile label={t('investments.kpi.totalValue')} value={formatCHF(portfolio.totalValue)} />
        <KpiTile label={t('investments.kpi.invested')} value={formatCHF(portfolio.totalCost)} />
        <KpiTile
          label={t('investments.kpi.gainLoss')}
          value={signed(portfolio.gainLoss, formatCHF(portfolio.gainLoss))}
          valueClass={amountColour(portfolio.gainLoss)}
        />
        <KpiTile
          label={t('investments.kpi.returnPct')}
          value={signed(portfolio.returnPct, formatPercent(portfolio.returnPct))}
          valueClass={amountColour(portfolio.returnPct)}
        />
      </div>
      {portfolio.hasUnconverted && (
        <p className="text-xs text-amber-600 dark:text-amber-400">
          {t('investments.kpi.partialTotal')}
        </p>
      )}
    </div>
  );
}
