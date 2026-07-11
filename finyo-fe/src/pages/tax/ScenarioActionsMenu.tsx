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
  const queryClient = useQueryClient();

  const invalidateScenarios = () => {
    queryClient.invalidateQueries({ queryKey: ['tax', 'scenarios', String(year)] });
  };

  const setDefault = useMutation({
    mutationFn: () => taxApi.setDefaultScenario(token, year, scenario.id),
    onSuccess: invalidateScenarios,
  });

  const deleteScenario = useMutation({
    mutationFn: () => taxApi.deleteScenario(token, year, scenario.id),
    onSuccess: () => {
      invalidateScenarios();
      onDeleted();
    },
  });

  const confirmDelete = () => {
    if (globalThis.confirm(t('tax.confirmDeleteScenario', { name: scenario.name }))) {
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
          aria-label={t('tax.scenarioActions')}
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
              {t('tax.setAsDefault')}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
          </>
        )}
        <DropdownMenuItem
          className="text-destructive focus:text-destructive"
          onSelect={confirmDelete}
        >
          <Trash2 className="h-4 w-4" />
          {t('tax.deleteScenario')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
