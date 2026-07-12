import { useTranslation } from 'react-i18next';
import { ScenarioChipBar } from '@/components/scenarios/ScenarioChipBar';
import type { ScenarioSliceBarProps } from '@/components/scenarios/ScenarioChipBar';
import type { Pillar3Scenario } from '@/api/pillar3';

/**
 * Scenario chips for the 3a calculator. The secondary metric shows the
 * product name, or the manual return rate.
 */
export function ScenarioBar({
  scenarios,
  selectedScenarioId,
  onSelect,
  onAdd,
  addDisabled,
  className,
}: Readonly<ScenarioSliceBarProps<Pillar3Scenario>>) {
  const { t } = useTranslation();

  const items = scenarios.map((scenario) => ({
    id: scenario.id,
    name: scenario.name,
    isDefault: scenario.isDefault,
    detail: scenario.product
      ? scenario.product.name
      : `${scenario.effectiveReturnPercent.toFixed(1)} %`,
  }));

  return (
    <ScenarioChipBar
      label={t('pillar3.tabs.scenarios')}
      newLabel={t('pillar3.scenarios.new')}
      items={items}
      selectedId={selectedScenarioId}
      onSelect={onSelect}
      onNew={onAdd}
      newDisabled={addDisabled}
      detailClassName="max-w-32 truncate"
      className={className}
    />
  );
}
