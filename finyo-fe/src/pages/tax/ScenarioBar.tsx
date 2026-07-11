import { useTranslation } from 'react-i18next';
import type { TaxScenario } from '@/api/tax';
import { cn } from '@/lib/utils';

/** Chip metric: Swiss apostrophe thousands without decimals (e.g. 7'258). */
function formatChipAmount(value: number): string {
  return value.toLocaleString('de-CH', { maximumFractionDigits: 0 });
}

interface ScenarioBarProps {
  scenarios: readonly TaxScenario[];
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
      aria-label={t('tax.scenarios')}
      className={cn('flex flex-wrap items-center gap-2', className)}
    >
      <span className="mr-1 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        {t('tax.scenarios')}
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
            {scenario.calculation && typeof scenario.inputs.pillar3aContribution === 'number' && (
              <span
                className={cn(
                  'text-[11px] tabular-nums',
                  active ? 'text-primary-foreground opacity-70' : 'text-muted-foreground',
                )}
              >
                3a {formatChipAmount(scenario.inputs.pillar3aContribution)}
              </span>
            )}
          </button>
        );
      })}
      <button
        type="button"
        onClick={onAdd}
        disabled={addDisabled}
        className="inline-flex items-center rounded-full border border-dashed bg-card px-3.5 py-1.5 text-[13px] font-semibold text-muted-foreground transition-colors hover:bg-secondary disabled:pointer-events-none disabled:opacity-50"
      >
        + {t('tax.newScenario')}
      </button>
    </div>
  );
}
