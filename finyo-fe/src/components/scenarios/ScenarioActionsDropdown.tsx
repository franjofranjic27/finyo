import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { QueryKey } from '@tanstack/react-query';
import { MoreVertical, Star, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

interface ScenarioActionsDropdownProps {
  /** Hides the set-default item when the scenario already is the default. */
  isDefault: boolean;
  actionsLabel: string;
  setAsDefaultLabel: string;
  deleteLabel: string;
  confirmDeleteMessage: string;
  /** Query key of the scenario list to refetch after a mutation. */
  queryKey: QueryKey;
  setDefault: () => Promise<unknown>;
  deleteScenario: () => Promise<unknown>;
  /** Called after the scenario was deleted (the caller clears its selection). */
  onDeleted?: () => void;
}

/** Set-default and delete dropdown shared by the scenario slices. */
export function ScenarioActionsDropdown({
  isDefault,
  actionsLabel,
  setAsDefaultLabel,
  deleteLabel,
  confirmDeleteMessage,
  queryKey,
  setDefault,
  deleteScenario,
  onDeleted,
}: Readonly<ScenarioActionsDropdownProps>) {
  const queryClient = useQueryClient();

  const invalidateScenarios = () => {
    queryClient.invalidateQueries({ queryKey });
  };

  const setDefaultMutation = useMutation({
    mutationFn: setDefault,
    onSuccess: invalidateScenarios,
  });

  const deleteMutation = useMutation({
    mutationFn: deleteScenario,
    onSuccess: () => {
      invalidateScenarios();
      onDeleted?.();
    },
  });

  const confirmDelete = () => {
    if (globalThis.confirm(confirmDeleteMessage)) {
      deleteMutation.mutate();
    }
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8 print:hidden"
          aria-label={actionsLabel}
          disabled={setDefaultMutation.isPending || deleteMutation.isPending}
        >
          <MoreVertical className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {!isDefault && (
          <>
            <DropdownMenuItem onSelect={() => setDefaultMutation.mutate()}>
              <Star className="h-4 w-4" />
              {setAsDefaultLabel}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
          </>
        )}
        <DropdownMenuItem
          className="text-destructive focus:text-destructive"
          onSelect={confirmDelete}
        >
          <Trash2 className="h-4 w-4" />
          {deleteLabel}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
