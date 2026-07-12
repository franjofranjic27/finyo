import { useTranslation } from 'react-i18next';
import { ScenarioActionsDropdown } from '@/components/scenarios/ScenarioActionsDropdown';
import { useAuth } from '@/auth/useAuth';
import { taxApi } from '@/api/tax';
import type { TaxScenario } from '@/api/tax';

interface ScenarioActionsMenuProps {
  year: number;
  scenario: TaxScenario;
  /** Called after the scenario was deleted (the page clears its selection). */
  onDeleted: () => void;
}

/** Set-default and delete for the selected scenario (breakdown card header). */
export function ScenarioActionsMenu({
  year,
  scenario,
  onDeleted,
}: Readonly<ScenarioActionsMenuProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  return (
    <ScenarioActionsDropdown
      isDefault={scenario.isDefault}
      actionsLabel={t('tax.scenarioActions')}
      setAsDefaultLabel={t('tax.setAsDefault')}
      deleteLabel={t('tax.deleteScenario')}
      confirmDeleteMessage={t('tax.confirmDeleteScenario', { name: scenario.name })}
      queryKey={['tax', 'scenarios', String(year)]}
      setDefault={() => taxApi.setDefaultScenario(token, year, scenario.id)}
      deleteScenario={() => taxApi.deleteScenario(token, year, scenario.id)}
      onDeleted={onDeleted}
    />
  );
}
