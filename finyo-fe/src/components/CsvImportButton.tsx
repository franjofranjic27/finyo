import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/auth/useAuth';

export interface CsvImportResult {
  created: number;
  updated: number;
  failed: number;
  errors: string[];
}

interface CsvImportButtonProps<T> {
  /** i18n prefix providing `csvImport`, `csvHint`, `csvResult` and `csvNoRows`. */
  i18nPrefix: string;
  parse: (csv: string) => T[];
  importItems: (token: string, items: T[]) => Promise<CsvImportResult>;
  /** Query keys to invalidate after a successful import. */
  invalidateKeys: readonly string[][];
}

/** Hidden file input plus button that parses a CSV and bulk-imports the rows. */
export function CsvImportButton<T>({
  i18nPrefix,
  parse,
  importItems,
  invalidateKeys,
}: Readonly<CsvImportButtonProps<T>>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);

  const [summary, setSummary] = useState<string | null>(null);
  const [rowErrors, setRowErrors] = useState<string[]>([]);

  const importMutation = useMutation({
    mutationFn: (items: T[]) => importItems(token, items),
    onSuccess: (result) => {
      for (const queryKey of invalidateKeys) {
        queryClient.invalidateQueries({ queryKey });
      }
      setSummary(t(`${i18nPrefix}.csvResult`, {
        created: result.created,
        updated: result.updated,
        failed: result.failed,
      }));
      setRowErrors(result.errors);
    },
  });

  const importFile = (file: File) => {
    const reader = new FileReader();
    reader.onload = () => {
      const items = parse(String(reader.result ?? ''));
      setRowErrors([]);
      if (items.length === 0) {
        setSummary(t(`${i18nPrefix}.csvNoRows`));
        return;
      }
      setSummary(null);
      importMutation.mutate(items);
    };
    reader.readAsText(file);
  };

  return (
    <div className="flex flex-col items-end gap-1">
      <input
        ref={inputRef}
        type="file"
        accept=".csv,text/csv"
        className="sr-only"
        aria-label={t(`${i18nPrefix}.csvImport`)}
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) importFile(file);
          // Allow re-importing the same file after a fix.
          e.target.value = '';
        }}
      />
      <Button
        variant="ghost"
        size="sm"
        title={t(`${i18nPrefix}.csvHint`)}
        onClick={() => inputRef.current?.click()}
        disabled={importMutation.isPending}
      >
        <Upload className="mr-1 h-4 w-4" />
        {importMutation.isPending ? t('common.loading') : t(`${i18nPrefix}.csvImport`)}
      </Button>
      {summary && <p className="text-xs text-muted-foreground">{summary}</p>}
      {importMutation.error && (
        <p className="text-xs text-destructive">{importMutation.error.message}</p>
      )}
      {rowErrors.length > 0 && (
        <ul className="text-xs text-destructive">
          {rowErrors.map((error) => (
            <li key={error}>{error}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
