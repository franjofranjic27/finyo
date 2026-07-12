import { useTranslation } from 'react-i18next';
import { ScenarioSaveForm } from '@/components/scenarios/ScenarioSaveForm';
import { useAuth } from '@/auth/useAuth';
import { pillar3Api } from '@/api/pillar3';
import type { Pillar3Scenario, Pillar3ScenarioInputs } from '@/api/pillar3';

interface ScenarioSavePanelProps {
  /** The calculator's current inputs — saved as the scenario snapshot. */
  inputs: Pillar3ScenarioInputs;
  /** Whether a default scenario already exists. */
  hasDefault: boolean;
  onClose: () => void;
  onSaved?: (scenario: Pillar3Scenario) => void;
}

/** Save panel for 3a scenarios; the shared form drives name and default flag. */
export function ScenarioSavePanel({
  inputs,
  hasDefault,
  onClose,
  onSaved,
}: Readonly<ScenarioSavePanelProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  // When a default already exists, create as non-default first and then
  // switch: the backend swaps atomically, avoiding a 409 on create.
  const saveScenario = async (name: string, makeDefault: boolean) => {
    const created = await pillar3Api.createScenario(token, {
      ...inputs,
      name,
      isDefault: makeDefault && !hasDefault,
    });
    return makeDefault && hasDefault
      ? pillar3Api.setDefaultScenario(token, created.id)
      : created;
  };

  return (
    <ScenarioSaveForm
      title={t('pillar3.scenarios.save')}
      nameLabel={t('pillar3.scenarios.name')}
      nameInputId="pillar3-scenario-name"
      defaultCheckboxLabel={t('pillar3.scenarios.setAsDefault')}
      queryKey={['pillar3-scenarios']}
      saveScenario={saveScenario}
      onClose={onClose}
      onSaved={onSaved}
    />
  );
}
