import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { MoreVertical, Star, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
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
  const queryClient = useQueryClient();

  const invalidateScenarios = () => {
    queryClient.invalidateQueries({ queryKey: ['pillar3-scenarios'] });
  };

  const setDefault = useMutation({
    mutationFn: () => pillar3Api.setDefaultScenario(token, scenario.id),
    onSuccess: invalidateScenarios,
  });

  const deleteScenario = useMutation({
    mutationFn: () => pillar3Api.deleteScenario(token, scenario.id),
    onSuccess: () => {
      invalidateScenarios();
      onDeleted?.();
    },
  });

  const confirmDelete = () => {
    if (globalThis.confirm(t('pillar3.scenarios.confirmDelete', { name: scenario.name }))) {
      deleteScenario.mutate();
    }
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8 print:hidden"
          aria-label={t('pillar3.scenarios.actions')}
          disabled={setDefault.isPending || deleteScenario.isPending}
        >
          <MoreVertical className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {!scenario.isDefault && (
          <>
            <DropdownMenuItem onSelect={() => setDefault.mutate()}>
              <Star className="h-4 w-4" />
              {t('pillar3.scenarios.setAsDefault')}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
          </>
        )}
        <DropdownMenuItem
          className="text-destructive focus:text-destructive"
          onSelect={confirmDelete}
        >
          <Trash2 className="h-4 w-4" />
          {t('pillar3.scenarios.delete')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
