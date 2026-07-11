import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/auth/useAuth';
import { portfolioApi } from '@/api/portfolio';
import type { CreatePositionRequest } from '@/api/portfolio';
import { parsePositionsCsv } from './csv';

export function CsvImportButton() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);

  const [summary, setSummary] = useState<string | null>(null);
  const [rowErrors, setRowErrors] = useState<string[]>([]);

  const importPositions = useMutation({
    mutationFn: (positions: CreatePositionRequest[]) =>
      portfolioApi.createPositionsBulk(token, positions),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      setSummary(t('investments.add.csvResult', {
        imported: result.imported,
        failed: result.failed,
      }));
      setRowErrors((previous) => [...previous, ...result.errors]);
    },
  });

  const importFile = (file: File) => {
    const reader = new FileReader();
    reader.onload = () => {
      const { positions, errors } = parsePositionsCsv(String(reader.result ?? ''));
      setRowErrors(errors);
      if (positions.length === 0) {
        setSummary(t('investments.add.csvNoRows'));
        return;
      }
      setSummary(null);
      importPositions.mutate(positions);
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
        aria-label={t('investments.add.csvImport')}
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) importFile(file);
          // Allow re-importing the same file after a fix.
          e.target.value = '';
        }}
      />
      <Button
        variant="outline"
        size="sm"
        onClick={() => inputRef.current?.click()}
        disabled={importPositions.isPending}
      >
        <Upload className="mr-1 h-4 w-4" />
        {importPositions.isPending ? t('common.loading') : t('investments.add.csvImport')}
      </Button>
      {summary && <p className="text-xs text-muted-foreground">{summary}</p>}
      {importPositions.error && (
        <p className="text-xs text-destructive">{importPositions.error.message}</p>
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
