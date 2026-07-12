import { cn } from '@/lib/utils';

export interface ScenarioChipItem {
  id: string;
  name: string;
  isDefault: boolean;
  /** Optional secondary metric rendered next to the name. */
  detail?: string;
}

/** Props contract for the slice-specific scenario bars built on ScenarioChipBar. */
export interface ScenarioSliceBarProps<TScenario> {
  scenarios: readonly TScenario[];
  selectedScenarioId: string | null;
  /** Called with the scenario id, or null when the active chip is clicked again. */
  onSelect: (scenarioId: string | null) => void;
  onAdd: () => void;
  addDisabled?: boolean;
  className?: string;
}

interface ScenarioChipBarProps {
  label: string;
  newLabel: string;
  items: readonly ScenarioChipItem[];
  selectedId: string | null;
  /** Called with the item id, or null when the active chip is clicked again. */
  onSelect: (id: string | null) => void;
  onNew: () => void;
  newDisabled?: boolean;
  /** Extra classes for the detail metric (e.g. truncation). */
  detailClassName?: string;
  className?: string;
}

/**
 * Pill-chip bar shared by the scenario slices: one chip per saved scenario
 * (star marks the default) plus a dashed "new scenario" chip.
 */
export function ScenarioChipBar({
  label,
  newLabel,
  items,
  selectedId,
  onSelect,
  onNew,
  newDisabled,
  detailClassName,
  className,
}: Readonly<ScenarioChipBarProps>) {
  return (
    <div
      role="group"
      aria-label={label}
      className={cn('flex flex-wrap items-center gap-2', className)}
    >
      <span className="mr-1 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        {label}
      </span>
      {items.map((item) => {
        const active = item.id === selectedId;
        return (
          <button
            key={item.id}
            type="button"
            aria-pressed={active}
            onClick={() => onSelect(active ? null : item.id)}
            className={cn(
              'inline-flex items-center gap-2 rounded-full border bg-card px-3.5 py-1.5 text-[13px] font-medium transition-colors',
              active ? 'border-primary bg-primary text-primary-foreground' : 'hover:bg-secondary',
            )}
          >
            {item.isDefault && (
              <span
                aria-hidden="true"
                className={cn('text-xs', active ? 'text-primary-foreground' : 'text-amber-500')}
              >
                ★
              </span>
            )}
            <span>{item.name}</span>
            {item.detail !== undefined && (
              <span
                className={cn(
                  'text-[11px] tabular-nums',
                  detailClassName,
                  active ? 'text-primary-foreground opacity-70' : 'text-muted-foreground',
                )}
              >
                {item.detail}
              </span>
            )}
          </button>
        );
      })}
      <button
        type="button"
        onClick={onNew}
        disabled={newDisabled}
        className="inline-flex items-center rounded-full border border-dashed bg-card px-3.5 py-1.5 text-[13px] font-semibold text-muted-foreground transition-colors hover:bg-secondary disabled:pointer-events-none disabled:opacity-50"
      >
        + {newLabel}
      </button>
    </div>
  );
}
