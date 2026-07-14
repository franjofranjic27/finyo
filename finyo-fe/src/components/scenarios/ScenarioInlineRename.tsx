import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { QueryKey } from '@tanstack/react-query';
import { Check, Pencil, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

interface ScenarioInlineRenameProps {
  name: string;
  isDefault: boolean;
  renameLabel: string;
  /** Query key of the scenario list to refetch after renaming. */
  queryKey: QueryKey;
  /** Persists the new name; the slices send the stored inputs unchanged. */
  onRename: (name: string) => Promise<unknown>;
}

/** Scenario name with a pencil toggle that swaps to an inline rename input. */
export function ScenarioInlineRename({
  name,
  isDefault,
  renameLabel,
  queryKey,
  onRename,
}: Readonly<ScenarioInlineRenameProps>) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(name);

  const rename = useMutation({
    mutationFn: () => onRename(draft.trim()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey });
      setEditing(false);
    },
  });

  const startEditing = () => {
    setDraft(name);
    rename.reset();
    setEditing(true);
  };

  if (!editing) {
    return (
      <div className="flex items-center gap-1.5">
        <p className="text-sm font-semibold">
          {isDefault && (
            <span aria-hidden="true" className="mr-1 text-amber-500">
              ★
            </span>
          )}
          {name}
        </p>
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-muted-foreground"
          aria-label={renameLabel}
          onClick={startEditing}
        >
          <Pencil className="h-3.5 w-3.5" />
        </Button>
      </div>
    );
  }

  return (
    <div>
      <div className="flex items-center gap-1.5">
        <Input
          autoFocus
          className="h-8 max-w-56 text-sm"
          value={draft}
          maxLength={100}
          aria-label={renameLabel}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setEditing(false);
          }}
        />
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7"
          aria-label={t('common.save')}
          disabled={!draft.trim() || rename.isPending}
          onClick={() => rename.mutate()}
        >
          <Check className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-muted-foreground"
          aria-label={t('common.cancel')}
          onClick={() => setEditing(false)}
        >
          <X className="h-4 w-4" />
        </Button>
      </div>
      {rename.error && (
        <p className="mt-1 text-xs text-destructive">{rename.error.message}</p>
      )}
    </div>
  );
}
