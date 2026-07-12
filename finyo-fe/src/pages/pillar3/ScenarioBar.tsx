import { useTranslation } from 'react-i18next';
import type { Pillar3Scenario } from '@/api/pillar3';
import { cn } from '@/lib/utils';

interface ScenarioBarProps {
  scenarios: readonly Pillar3Scenario[];
  selectedScenarioId: string | null;
  /** Called with the scenario id, or null when the active chip is clicked again. */
  onSelect: (scenarioId: string | null) => void;
  onAdd: () => void;
  addDisabled?: boolean;
  className?: string;
}

/**
 * Pill-chip bar between the calculator and the results: one chip per saved
 * scenario (star marks the default) plus a dashed "new scenario" chip.
 * The secondary metric shows the product name, or the manual return rate.
 */
export function ScenarioBar({
  scenarios,
  selectedScenarioId,
  onSelect,
  onAdd,
  addDisabled,
  className,
}: Readonly<ScenarioBarProps>) {
  const { t } = useTranslation();

  return (
    <div
      role="group"
      aria-label={t('pillar3.tabs.scenarios')}
      className={cn('flex flex-wrap items-center gap-2', className)}
    >
      <span className="mr-1 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        {t('pillar3.tabs.scenarios')}
      </span>
      {scenarios.map((scenario) => {
        const active = scenario.id === selectedScenarioId;
        return (
          <button
            key={scenario.id}
            type="button"
            aria-pressed={active}
            onClick={() => onSelect(active ? null : scenario.id)}
            className={cn(
              'inline-flex items-center gap-2 rounded-full border bg-card px-3.5 py-1.5 text-[13px] font-medium transition-colors',
              active ? 'border-primary bg-primary text-primary-foreground' : 'hover:bg-secondary',
            )}
          >
            {scenario.isDefault && (
              <span
                aria-hidden="true"
                className={cn('text-xs', active ? 'text-primary-foreground' : 'text-amber-500')}
              >
                ★
              </span>
            )}
            <span>{scenario.name}</span>
            <span
              className={cn(
                'max-w-32 truncate text-[11px] tabular-nums',
                active ? 'text-primary-foreground opacity-70' : 'text-muted-foreground',
              )}
            >
              {scenario.product
                ? scenario.product.name
                : `${scenario.effectiveReturnPercent.toFixed(1)} %`}
            </span>
          </button>
        );
      })}
      <button
        type="button"
        onClick={onAdd}
        disabled={addDisabled}
        className="inline-flex items-center rounded-full border border-dashed bg-card px-3.5 py-1.5 text-[13px] font-semibold text-muted-foreground transition-colors hover:bg-secondary disabled:pointer-events-none disabled:opacity-50"
      >
        + {t('pillar3.scenarios.new')}
      </button>
    </div>
  );
}
