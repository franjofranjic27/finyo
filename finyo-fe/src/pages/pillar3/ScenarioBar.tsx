import { useTranslation } from 'react-i18next';
import { ScenarioChipBar } from '@/components/scenarios/ScenarioChipBar';
import type { ScenarioSliceBarProps } from '@/components/scenarios/ScenarioChipBar';
import type { Pillar3Scenario } from '@/api/pillar3';

interface Pillar3ScenarioBarProps extends ScenarioSliceBarProps<Pillar3Scenario> {
  /**
   * Makes the product badge of a chip clickable: called with the scenario
   * whose linked product should be shown. Chips with a manual return keep
   * their plain metric either way.
   */
  onProductDetail?: (scenario: Pillar3Scenario) => void;
}

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
  onProductDetail,
}: Readonly<Pillar3ScenarioBarProps>) {
  const { t } = useTranslation();

  const items = scenarios.map((scenario) => ({
    id: scenario.id,
    name: scenario.name,
    isDefault: scenario.isDefault,
    detail: scenario.product
      ? scenario.product.name
      : `${scenario.effectiveReturnPercent.toFixed(1)} %`,
    ...(scenario.product && onProductDetail
      ? {
          onDetailClick: () => onProductDetail(scenario),
          detailAriaLabel: t('pillar3.productDetail.open', { name: scenario.product.name }),
        }
      : {}),
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
