import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { QueryKey } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

interface ScenarioSaveFormProps<TScenario> {
  title: string;
  nameLabel: string;
  nameInputId: string;
  defaultCheckboxLabel: string;
  /**
   * Forces the scenario to be saved as the default (the checkbox is hidden and
   * `forcedDefaultHint` is shown instead) — used for the very first scenario.
   */
  forceDefault?: boolean;
  forcedDefaultHint?: string;
  /** Query key of the scenario list to refetch after saving. */
  queryKey: QueryKey;
  /** Persists the scenario; the slices implement the create(-then-promote) flow. */
  saveScenario: (name: string, makeDefault: boolean) => Promise<TScenario>;
  onClose: () => void;
  onSaved?: (scenario: TScenario) => void;
}

/** Inline save panel below the calculator actions (design: no modal). */
export function ScenarioSaveForm<TScenario>({
  title,
  nameLabel,
  nameInputId,
  defaultCheckboxLabel,
  forceDefault = false,
  forcedDefaultHint,
  queryKey,
  saveScenario,
  onClose,
  onSaved,
}: Readonly<ScenarioSaveFormProps<TScenario>>) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [name, setName] = useState('');
  const [makeDefault, setMakeDefault] = useState(false);

  const save = useMutation({
    mutationFn: () => saveScenario(name.trim(), forceDefault || makeDefault),
    onSuccess: (scenario) => {
      onSaved?.(scenario);
    },
    // The create may succeed while the default switch fails; always refetch so
    // the persisted scenario shows up and a retry doesn't silently duplicate it.
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey });
    },
  });

  return (
    <div className="mt-4 rounded-lg border bg-secondary p-4 print:hidden">
      <p className="text-[13px] font-semibold">{title}</p>
      <div className="mt-3 max-w-sm space-y-1.5">
        <Label htmlFor={nameInputId} className="text-xs text-muted-foreground">
          {nameLabel}
        </Label>
        <Input
          id={nameInputId}
          className="bg-card"
          value={name}
          maxLength={100}
          onChange={(e) => setName(e.target.value)}
        />
      </div>
      {forceDefault ? (
        forcedDefaultHint && (
          <p className="mt-3 text-xs text-muted-foreground">{forcedDefaultHint}</p>
        )
      ) : (
        <label className="mt-3 flex w-fit cursor-pointer select-none items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="h-4 w-4 accent-indigo-500"
            checked={makeDefault}
            onChange={(e) => setMakeDefault(e.target.checked)}
          />
          {defaultCheckboxLabel}
        </label>
      )}
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
