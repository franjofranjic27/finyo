import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
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

/** Inline save panel below the calculator actions (design: no modal). */
export function ScenarioSavePanel({
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
      const created = await pillar3Api.createScenario(token, {
        ...inputs,
        name: name.trim(),
        isDefault: makeDefault && !hasDefault,
      });
      return makeDefault && hasDefault
        ? pillar3Api.setDefaultScenario(token, created.id)
        : created;
    },
    onSuccess: (scenario) => {
      onSaved?.(scenario);
    },
    // The create may succeed while the default switch fails; always refetch so
    // the persisted scenario shows up and a retry doesn't silently duplicate it.
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['pillar3-scenarios'] });
    },
  });

  return (
    <div className="mt-4 rounded-lg border bg-secondary p-4 print:hidden">
      <p className="text-[13px] font-semibold">{t('pillar3.scenarios.save')}</p>
      <div className="mt-3 max-w-sm space-y-1.5">
        <Label htmlFor="pillar3-scenario-name" className="text-xs text-muted-foreground">
          {t('pillar3.scenarios.name')}
        </Label>
        <Input
          id="pillar3-scenario-name"
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
        {t('pillar3.scenarios.setAsDefault')}
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
