import { Fragment } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Separator } from '@/components/ui/separator';
import { cn } from '@/lib/utils';
import { YearStatusBadge } from './YearStatusBadge';
import type { YearListEntry } from './taxYearUtils';

interface YearSidebarProps {
  entries: YearListEntry[];
  selectedYear: number;
  /** `vertical` renders the desktop sidebar list, `horizontal` the mobile chip strip. */
  orientation?: 'vertical' | 'horizontal';
}

export function YearSidebar({ entries, selectedYear, orientation = 'vertical' }: YearSidebarProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const selectYear = (entry: YearListEntry) => {
    if (!entry.clickable) return;
    navigate(`/tax/${entry.year}`);
  };

  if (orientation === 'horizontal') {
    return (
      <div className="flex gap-2 overflow-x-auto pb-1">
        {entries.map((entry) => {
          const selected = entry.year === selectedYear;
          return (
            <button
              key={entry.year}
              type="button"
              disabled={!entry.clickable}
              onClick={() => selectYear(entry)}
              className={cn(
                'flex shrink-0 items-center gap-2 rounded-full border px-3 py-1 text-sm transition-colors',
                selected
                  ? 'border-primary bg-primary text-primary-foreground'
                  : 'hover:bg-muted',
                !entry.clickable && 'cursor-default opacity-60',
              )}
            >
              {entry.year}
              <YearStatusBadge status={entry.displayStatus} inverted={selected} />
            </button>
          );
        })}
      </div>
    );
  }

  return (
    <div>
      <p className="mb-2 px-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {t('tax.yearSidebarTitle')}
      </p>
      <nav className="space-y-1">
        {entries.map((entry, index) => {
          const selected = entry.year === selectedYear;
          const isLast = index === entries.length - 1;
          return (
            <Fragment key={entry.year}>
              <button
                type="button"
                disabled={!entry.clickable}
                onClick={() => selectYear(entry)}
                className={cn(
                  'flex w-full items-center justify-between gap-2 rounded-md px-2 py-1.5 text-sm transition-colors',
                  selected ? 'bg-primary text-primary-foreground' : 'hover:bg-muted',
                  !entry.clickable && 'cursor-default opacity-60',
                )}
              >
                <span className="font-medium">{entry.year}</span>
                <YearStatusBadge status={entry.displayStatus} inverted={selected} />
              </button>
              {entry.displayStatus === 'current' && !isLast && <Separator className="my-2" />}
            </Fragment>
          );
        })}
      </nav>
    </div>
  );
}
