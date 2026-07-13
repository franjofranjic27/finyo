import { useTranslation } from 'react-i18next';
import { Progress } from '@/components/ui/progress';
import { formatCHF, formatPercent } from '@/lib/formatters';

interface PlanVsActualRowProps {
  label: string;
  hint?: string;
  planned: number;
  actual: number;
  /** For expenses "over plan" is bad; for income "under plan" is the shortfall. */
  overIsBad: boolean;
}

/** One plan-vs-actual line: amounts, progress bar and share of the plan. */
export function PlanVsActualRow({
  label,
  hint,
  planned,
  actual,
  overIsBad,
}: Readonly<PlanVsActualRowProps>) {
  const { t } = useTranslation();
  const pct = planned > 0 ? (actual / planned) * 100 : 0;
  const over = overIsBad && actual > planned;

  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between gap-2 text-sm">
        <span className="font-medium">{label}</span>
        <span className={`tabular-nums ${over ? 'font-semibold text-destructive' : ''}`}>
          {formatCHF(actual)}
          <span className="text-muted-foreground">
            {' '}
            / {formatCHF(planned)} {t('budget.month.plan')}
          </span>
        </span>
      </div>
      <Progress
        value={Math.min(100, Math.max(0, pct))}
        className={over ? '[&>div]:bg-destructive' : ''}
      />
      <div className="flex justify-between text-xs text-muted-foreground">
        <span>{hint}</span>
        <span className={over ? 'font-medium text-destructive' : ''}>{formatPercent(pct)}</span>
      </div>
    </div>
  );
}
