import { useTranslation } from 'react-i18next';
import { ScenarioActionsDropdown } from '@/components/scenarios/ScenarioActionsDropdown';
import { useAuth } from '@/auth/useAuth';
import { pillar3Api } from '@/api/pillar3';
import type { Pillar3Scenario } from '@/api/pillar3';

interface ScenarioActionsMenuProps {
  scenario: Pillar3Scenario;
  /** Called after the scenario was deleted (the caller clears its selection). */
  onDeleted?: () => void;
}

/** Set-default and delete for a saved scenario (result header and table rows). */
export function ScenarioActionsMenu({
  scenario,
  onDeleted,
}: Readonly<ScenarioActionsMenuProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  return (
    <ScenarioActionsDropdown
      isDefault={scenario.isDefault}
      actionsLabel={t('pillar3.scenarios.actions')}
      setAsDefaultLabel={t('pillar3.scenarios.setAsDefault')}
      deleteLabel={t('pillar3.scenarios.delete')}
      confirmDeleteMessage={t('pillar3.scenarios.confirmDelete', { name: scenario.name })}
      queryKey={['pillar3-scenarios']}
      setDefault={() => pillar3Api.setDefaultScenario(token, scenario.id)}
      deleteScenario={() => pillar3Api.deleteScenario(token, scenario.id)}
      onDeleted={onDeleted}
    />
  );
}
