import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
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

/** Inline save panel below the calculator actions (design: no modal). */
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
  const queryClient = useQueryClient();

  const [name, setName] = useState('');
  const [makeDefault, setMakeDefault] = useState(false);

  const save = useMutation({
    mutationFn: async () => {
      // When a default already exists, create as non-default first and then
      // switch: the backend swaps atomically, avoiding a 409 on create.
      const created = await taxApi.createScenario(token, year, {
        ...inputs,
        name: name.trim(),
        isDefault: makeDefault && !hasDefault,
      });
      return makeDefault && hasDefault
        ? taxApi.setDefaultScenario(token, year, created.id)
        : created;
    },
    onSuccess: (scenario) => {
      onSaved(scenario);
    },
    // The create may succeed while the default switch fails; always refetch so
    // the persisted scenario shows up and a retry doesn't silently duplicate it.
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['tax', 'scenarios', String(year)] });
    },
  });

  return (
    <div className="mt-4 rounded-lg border bg-secondary p-4 print:hidden">
      <p className="text-[13px] font-semibold">{t('tax.saveScenario')}</p>
      <div className="mt-3 max-w-sm space-y-1.5">
        <Label htmlFor="scenario-name" className="text-xs text-muted-foreground">
          {t('tax.scenarioName')}
        </Label>
        <Input
          id="scenario-name"
          className="bg-card"
          value={name}
          maxLength={100}
          onChange={(e) => setName(e.target.value)}
        />
      </div>
      <label className="mt-3 flex w-fit cursor-pointer select-none items-center gap-2 text-sm">
        <input
          type="checkbox"
          className="h-4 w-4 accent-indigo-500"
          checked={makeDefault}
          onChange={(e) => setMakeDefault(e.target.checked)}
        />
        {t('tax.setAsDefaultFor', { year })}
      </label>
      {save.error && <p className="mt-2 text-sm text-destructive">{save.error.message}</p>}
      <div className="mt-3 flex gap-2">
        <Button onClick={() => save.mutate()} disabled={!name.trim() || save.isPending}>
          {save.isPending ? t('common.loading') : t('common.save')}
        </Button>
        <Button variant="outline" onClick={onClose}>
          {t('common.cancel')}
        </Button>
      </div>
    </div>
  );
}
