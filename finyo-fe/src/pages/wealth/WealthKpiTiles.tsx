import { useTranslation } from 'react-i18next';
import { Card, CardContent } from '@/components/ui/card';
import { formatCHF, formatDate, formatPercent, amountColour } from '@/lib/formatters';
import type { WealthOverview } from '@/api/wealth';

/** Prefixes positive amounts with "+" so gains read as "+CHF 1'000.00". */
function signed(value: number, formatted: string): string {
  return value > 0 ? `+${formatted}` : formatted;
}

function KpiTile({ label, value, sub, valueClass, subClass }: Readonly<{
  label: string;
  value: string;
  sub?: string;
  valueClass?: string;
  subClass?: string;
}>) {
  return (
    <Card>
      <CardContent className="pt-4">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className={`mt-1 text-xl font-bold tabular-nums ${valueClass ?? 'text-foreground'}`}>
          {value}
        </p>
        {sub && <p className={`mt-0.5 text-xs ${subClass ?? 'text-muted-foreground'}`}>{sub}</p>}
      </CardContent>
    </Card>
  );
}

export function WealthKpiTiles({ overview }: Readonly<{ overview: WealthOverview }>) {
  const { t, i18n } = useTranslation();
  const { ytdChange, ytdChangePct } = overview;

  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <KpiTile
        label={t('wealth.kpi.total')}
        value={formatCHF(overview.total)}
        sub={t('wealth.kpi.asOf', { date: formatDate(overview.asOf, i18n.language) })}
      />
      {ytdChange !== null && ytdChangePct !== null ? (
        <KpiTile
          label={t('wealth.kpi.ytd')}
          value={signed(ytdChange, formatCHF(ytdChange))}
          valueClass={amountColour(ytdChange)}
          sub={signed(ytdChangePct, formatPercent(ytdChangePct))}
          subClass={amountColour(ytdChangePct)}
        />
      ) : (
        <KpiTile
          label={t('wealth.kpi.ytd')}
          value="—"
          valueClass="text-muted-foreground"
          sub={t('wealth.kpi.ytdEmpty')}
        />
      )}
      <KpiTile
        label={t('wealth.kpi.monthlyRate')}
        value={formatCHF(overview.totalMonthlyRate)}
        sub={t('wealth.kpi.monthlyRateHint')}
      />
      <KpiTile
        label={t('wealth.kpi.forecast')}
        value={formatCHF(overview.totalForecastYearEnd)}
        sub={t('wealth.kpi.forecastHint')}
      />
    </div>
  );
}
