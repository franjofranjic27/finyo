import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import { STATUS_BADGE, STATUS_LABEL_KEY } from './taxYearUtils';
import type { DisplayStatus } from './taxYearUtils';

interface YearStatusBadgeProps {
  status: DisplayStatus;
  /** Inverted colours for use on a selected (primary-coloured) row. */
  inverted?: boolean;
}

export function YearStatusBadge({ status, inverted = false }: Readonly<YearStatusBadgeProps>) {
  const { t } = useTranslation();

  return (
    <span
      className={cn(
        'shrink-0 rounded-full px-2 py-0.5 text-[10px] font-medium',
        inverted ? 'bg-primary-foreground/20 text-primary-foreground' : STATUS_BADGE[status],
      )}
    >
      {t(STATUS_LABEL_KEY[status])}
    </span>
  );
}
