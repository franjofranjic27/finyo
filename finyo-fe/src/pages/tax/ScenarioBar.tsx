import { useTranslation } from 'react-i18next';
import { ScenarioChipBar } from '@/components/scenarios/ScenarioChipBar';
import type { ScenarioSliceBarProps } from '@/components/scenarios/ScenarioChipBar';
import type { TaxScenario } from '@/api/tax';

/** Chip metric: Swiss apostrophe thousands without decimals (e.g. 7'258). */
function formatChipAmount(value: number): string {
  return value.toLocaleString('de-CH', { maximumFractionDigits: 0 });
}

/** Scenario chips for a tax year; the metric shows the 3a contribution. */
export function ScenarioBar({
  scenarios,
  selectedScenarioId,
  onSelect,
  onAdd,
  addDisabled,
  className,
}: Readonly<ScenarioSliceBarProps<TaxScenario>>) {
  const { t } = useTranslation();

  const items = scenarios.map((scenario) => ({
    id: scenario.id,
    name: scenario.name,
    isDefault: scenario.isDefault,
    detail:
      scenario.calculation && typeof scenario.inputs.pillar3aContribution === 'number'
        ? `3a ${formatChipAmount(scenario.inputs.pillar3aContribution)}`
        : undefined,
  }));

  return (
    <ScenarioChipBar
      label={t('tax.scenarios')}
      newLabel={t('tax.newScenario')}
      items={items}
      selectedId={selectedScenarioId}
      onSelect={onSelect}
      onNew={onAdd}
      newDisabled={addDisabled}
      className={className}
    />
  );
}
