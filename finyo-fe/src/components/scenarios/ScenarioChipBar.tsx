import { cn } from '@/lib/utils';

export interface ScenarioChipItem {
  id: string;
  name: string;
  isDefault: boolean;
  /** Optional secondary metric rendered next to the name. */
  detail?: string;
  /**
   * When set, the detail metric becomes its own button inside the chip that
   * fires this callback instead of selecting the scenario (e.g. to open a
   * product detail dialog). Only honoured when `detail` is present.
   */
  onDetailClick?: () => void;
  /** Accessible name for the detail button; falls back to the visible detail text. */
  detailAriaLabel?: string;
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

const CHIP_SHAPE =
  'inline-flex shrink-0 items-center whitespace-nowrap rounded-full border bg-card text-[13px] font-medium transition-colors sm:whitespace-normal';

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
      // Mobile: one scrollable row; sm+ keeps the original wrapping layout.
      className={cn('flex items-center gap-2 overflow-x-auto sm:flex-wrap sm:overflow-x-visible', className)}
    >
      <span className="mr-1 shrink-0 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        {label}
      </span>
      {items.map((item) => (
        <ScenarioChip
          key={item.id}
          item={item}
          active={item.id === selectedId}
          onSelect={onSelect}
          detailClassName={detailClassName}
        />
      ))}
      <button
        type="button"
        onClick={onNew}
        disabled={newDisabled}
        className="inline-flex shrink-0 items-center whitespace-nowrap rounded-full border border-dashed bg-card px-3.5 py-1.5 text-[13px] font-semibold text-muted-foreground transition-colors hover:bg-secondary disabled:pointer-events-none disabled:opacity-50 sm:whitespace-normal"
      >
        + {newLabel}
      </button>
    </div>
  );
}

interface ScenarioChipProps {
  item: ScenarioChipItem;
  active: boolean;
  onSelect: (id: string | null) => void;
  detailClassName?: string;
}

function ScenarioChip({ item, active, onSelect, detailClassName }: Readonly<ScenarioChipProps>) {
  const activeClasses = active
    ? 'border-primary bg-primary text-primary-foreground'
    : 'hover:bg-secondary';
  const star = item.isDefault && (
    <span
      aria-hidden="true"
      className={cn('text-xs', active ? 'text-primary-foreground' : 'text-amber-500')}
    >
      ★
    </span>
  );
  const detail = item.detail !== undefined && (
    <span
      className={cn(
        'text-[11px] tabular-nums',
        detailClassName,
        active ? 'text-primary-foreground opacity-70' : 'text-muted-foreground',
      )}
    >
      {item.detail}
    </span>
  );

  // An interactive detail must not live inside the select button (nested
  // buttons are invalid HTML), so such a chip splits into a pill-styled group
  // holding two sibling buttons: select (star + name) and detail.
  if (item.onDetailClick && item.detail !== undefined) {
    return (
      <div className={cn(CHIP_SHAPE, activeClasses)}>
        <button
          type="button"
          aria-pressed={active}
          onClick={() => onSelect(active ? null : item.id)}
          className="inline-flex items-center gap-2 py-1.5 pl-3.5"
        >
          {star}
          <span>{item.name}</span>
        </button>
        <button
          type="button"
          aria-label={item.detailAriaLabel ?? item.detail}
          onClick={item.onDetailClick}
          className="inline-flex items-center py-1.5 pl-2 pr-3.5 underline-offset-2 hover:underline"
        >
          {detail}
        </button>
      </div>
    );
  }

  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={() => onSelect(active ? null : item.id)}
      className={cn(CHIP_SHAPE, 'gap-2 px-3.5 py-1.5', activeClasses)}
    >
      {star}
      <span>{item.name}</span>
      {detail}
    </button>
  );
}
