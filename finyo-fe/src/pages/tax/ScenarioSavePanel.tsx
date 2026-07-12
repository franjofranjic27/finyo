import { useTranslation } from 'react-i18next';
import { ScenarioSaveForm } from '@/components/scenarios/ScenarioSaveForm';
import { useAuth } from '@/auth/useAuth';
import { taxApi } from '@/api/tax';
import type { TaxScenario, TaxYearInputs } from '@/api/tax';

interface ScenarioSavePanelProps {
  year: number;
  /** The year's current inputs — saved as the scenario snapshot. */
  inputs: TaxYearInputs;
  /** Whether a default scenario already exists for the year. */
  hasDefault: boolean;
  onClose: () => void;
  onSaved: (scenario: TaxScenario) => void;
}

/** Save panel for a tax year's scenarios; the shared form drives the inputs. */
export function ScenarioSavePanel({
  year,
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
    const created = await taxApi.createScenario(token, year, {
      ...inputs,
      name,
      isDefault: makeDefault && !hasDefault,
    });
    return makeDefault && hasDefault
      ? taxApi.setDefaultScenario(token, year, created.id)
      : created;
  };

  return (
    <ScenarioSaveForm
      title={t('tax.saveScenario')}
      nameLabel={t('tax.scenarioName')}
      nameInputId="scenario-name"
      defaultCheckboxLabel={t('tax.setAsDefaultFor', { year })}
      queryKey={['tax', 'scenarios', String(year)]}
      saveScenario={saveScenario}
      onClose={onClose}
      onSaved={onSaved}
    />
  );
}
