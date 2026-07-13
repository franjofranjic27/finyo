import { useRef, useState } from 'react';
import type { DragEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronDown, FileText, Loader2, Upload, X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Collapsible, CollapsibleContent, CollapsibleTrigger,
} from '@/components/ui/collapsible';
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { useAuth } from '@/auth/useAuth';
import { accountsApi } from '@/api/accounts';
import { ApiError } from '@/api/client';
import { previewImport, transactionsApi } from '@/api/transactions';
import type {
  CsvPreset, ImportCommitResult, ImportFormat, ImportPreview, ImportPreviewRow,
} from '@/api/transactions';
import { amountColour, formatCHF, formatDate, formatFileSize } from '@/lib/formatters';

const MAX_FILE_BYTES = 10 * 1024 * 1024;
const ACCEPTED_EXTENSIONS = ['.csv', '.xlsx', '.xml'];
const CSV_PRESETS: readonly CsvPreset[] = ['CUSTOM', 'UBS', 'RAIFFEISEN', 'POSTFINANCE'];

function detectFormat(file: File): ImportFormat {
  const name = file.name.toLowerCase();
  if (name.endsWith('.xml')) return 'CAMT053';
  if (name.endsWith('.xlsx')) return 'EXCEL';
  return 'CSV';
}

function isAcceptedFile(file: File): boolean {
  const name = file.name.toLowerCase();
  return ACCEPTED_EXTENSIONS.some((extension) => name.endsWith(extension));
}

function previewErrorMessage(error: Error, t: (key: string) => string): string {
  if (error instanceof ApiError) {
    if (error.status === 413) return t('budget.import.errors.tooLarge');
    if (error.status === 422) {
      return error.message.startsWith('Request failed')
        ? t('budget.import.errors.unprocessable')
        : error.message;
    }
  }
  return error.message;
}

/**
 * Duplicates carrying a bank reference are unique per user by design — the
 * backend always skips them, so they cannot be opted into.
 */
function isLocked(row: ImportPreviewRow): boolean {
  return row.duplicate && !!row.externalRef;
}

interface StatementImportDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Two-step statement import: upload for a server-side dry-run preview, then
 * commit only the rows the user confirmed. Mirrors the tax-upload UX.
 */
export function StatementImportDialog({
  open,
  onOpenChange,
}: Readonly<StatementImportDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [accountId, setAccountId] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [preset, setPreset] = useState<CsvPreset>('CUSTOM');
  const [checked, setChecked] = useState<Record<number, boolean>>({});
  const [clientError, setClientError] = useState<string | null>(null);

  const accounts = useQuery({
    queryKey: ['accounts'],
    queryFn: () => accountsApi.getAll(token),
    enabled: !!token && open,
  });

  const preview = useMutation({
    mutationFn: (input: { file: File; accountId: string; preset: CsvPreset }) => {
      const format = detectFormat(input.file);
      return previewImport(token, input.file, input.accountId, {
        format,
        ...(format === 'CSV' ? { preset: input.preset } : {}),
      });
    },
    onSuccess: (result) => {
      // Non-duplicates are pre-checked; duplicates need an explicit opt-in.
      setChecked(
        Object.fromEntries(result.rows.map((row) => [row.index, !row.duplicate])),
      );
    },
  });

  const commit = useMutation({
    mutationFn: (data: ImportPreview) => {
      const rows = data.rows.filter((row) => checked[row.index]);
      return transactionsApi.commitImport(token, {
        accountId,
        format: data.format,
        // The flag only governs the date/amount/description heuristic: checking a
        // flagged row means "import it anyway". Rows with a bank reference cannot be
        // checked (see isLocked) and are skipped server-side in any case, so this
        // never runs the batch into the unique-reference conflict.
        skipDuplicates: !rows.some((row) => row.duplicate),
        rows: rows.map((row) => ({
          date: row.date,
          amount: row.amount,
          currency: row.currency,
          description: row.description,
          externalRef: row.externalRef,
          categoryId: row.suggestedCategoryId,
        })),
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['analytics'] });
    },
  });

  const reset = () => {
    setAccountId('');
    setFile(null);
    setPreset('CUSTOM');
    setChecked({});
    setClientError(null);
    preview.reset();
    commit.reset();
  };

  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen) reset();
    onOpenChange(nextOpen);
  };

  const maybePreview = (nextFile: File | null, nextAccountId: string, nextPreset: CsvPreset) => {
    if (nextFile && nextAccountId) {
      commit.reset();
      preview.mutate({ file: nextFile, accountId: nextAccountId, preset: nextPreset });
    }
  };

  const rejectFile = (message: string) => {
    // drop the rejected file so a later account or preset change cannot re-preview it
    setFile(null);
    setClientError(message);
  };

  const handleFile = (selected: File) => {
    setClientError(null);
    preview.reset();
    if (!isAcceptedFile(selected)) {
      rejectFile(t('budget.import.errors.invalidFile'));
      return;
    }
    if (selected.size > MAX_FILE_BYTES) {
      rejectFile(t('budget.import.errors.tooLarge'));
      return;
    }
    setFile(selected);
    maybePreview(selected, accountId, preset);
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    if (preview.isPending) return;
    const dropped = event.dataTransfer.files?.[0];
    if (dropped) handleFile(dropped);
  };

  const checkedCount = preview.data
    ? preview.data.rows.filter((row) => checked[row.index]).length
    : 0;
  const showPresetSelect = !file || detectFormat(file) === 'CSV';

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{t('budget.import.title')}</DialogTitle>
          <DialogDescription>{t('budget.import.subtitle')}</DialogDescription>
        </DialogHeader>

        {commit.data ? (
          <ImportResult result={commit.data} onClose={() => handleOpenChange(false)} />
        ) : (
          <div className="space-y-4">
            <div className="flex flex-wrap items-end gap-4">
              <div className="min-w-48 space-y-1">
                <Label htmlFor="import-account">{t('budget.import.account')}</Label>
                <Select
                  value={accountId}
                  onValueChange={(value) => {
                    setAccountId(value);
                    maybePreview(file, value, preset);
                  }}
                >
                  <SelectTrigger id="import-account">
                    <SelectValue placeholder={t('budget.import.accountPlaceholder')} />
                  </SelectTrigger>
                  <SelectContent>
                    {(accounts.data ?? []).map((account) => (
                      <SelectItem key={account.id} value={account.id}>
                        {account.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              {showPresetSelect && (
                <div className="min-w-40 space-y-1">
                  <Label htmlFor="import-preset">{t('budget.import.preset')}</Label>
                  <Select
                    value={preset}
                    onValueChange={(value) => {
                      const nextPreset = value as CsvPreset;
                      setPreset(nextPreset);
                      maybePreview(file, accountId, nextPreset);
                    }}
                  >
                    <SelectTrigger id="import-preset">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {CSV_PRESETS.map((value) => (
                        <SelectItem key={value} value={value}>
                          {value === 'CUSTOM' ? t('budget.import.presetStandard') : value}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}
            </div>

            {accounts.data && accounts.data.length === 0 && (
              <p className="text-sm text-amber-600 dark:text-amber-500">
                {t('budget.import.accountMissing')}{' '}
                <Link to="/accounts" className="underline">
                  {t('budget.import.accountsLink')}
                </Link>
              </p>
            )}

            {/* while a re-preview (account or preset change) is in flight the previous
                table would be stale — show the analysing state instead */}
            {preview.data && file && !preview.isPending ? (
              <ImportPreviewPanel
                file={file}
                preview={preview.data}
                checked={checked}
                checkedCount={checkedCount}
                commitPending={commit.isPending}
                commitErrorMessage={commit.error?.message ?? null}
                onToggle={(index, value) =>
                  setChecked((previous) => ({ ...previous, [index]: value }))
                }
                onCommit={() => preview.data && commit.mutate(preview.data)}
                onDiscard={() => {
                  setFile(null);
                  preview.reset();
                }}
              />
            ) : (
              <>
                <Dropzone
                  isProcessing={preview.isPending}
                  onDrop={handleDrop}
                  onBrowse={() => fileInputRef.current?.click()}
                />
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".csv,.xlsx,.xml"
                  className="sr-only"
                  aria-label={t('budget.import.chooseFile')}
                  onChange={(event) => {
                    const selected = event.target.files?.[0];
                    if (selected) handleFile(selected);
                    // Allow re-selecting the same file after a fix.
                    event.target.value = '';
                  }}
                />
              </>
            )}

            {clientError && <p className="text-sm text-destructive">{clientError}</p>}
            {preview.error && (
              <p className="text-sm text-destructive">
                {previewErrorMessage(preview.error, t)}
              </p>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

interface DropzoneProps {
  isProcessing: boolean;
  onDrop: (event: DragEvent<HTMLDivElement>) => void;
  onBrowse: () => void;
}

function Dropzone({ isProcessing, onDrop, onBrowse }: Readonly<DropzoneProps>) {
  const { t } = useTranslation();
  return (
    <div
      className="flex flex-col items-center gap-2 rounded-lg border-2 border-dashed p-8 text-center"
      onDrop={onDrop}
      onDragOver={(event) => event.preventDefault()}
    >
      {isProcessing ? (
        <>
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          <p className="text-sm text-muted-foreground">{t('budget.import.analyzing')}</p>
        </>
      ) : (
        <>
          <Upload className="h-6 w-6 text-muted-foreground" />
          <p className="text-sm font-medium">{t('budget.import.dropzone')}</p>
          <Button variant="outline" size="sm" onClick={onBrowse}>
            {t('budget.import.chooseFile')}
          </Button>
          <p className="text-xs text-muted-foreground">{t('budget.import.dropzoneMax')}</p>
        </>
      )}
    </div>
  );
}

function DuplicatePill({ locked }: Readonly<{ locked: boolean }>) {
  const { t } = useTranslation();
  return (
    <span
      title={locked ? t('budget.import.duplicateLocked') : undefined}
      className="inline-flex shrink-0 items-center rounded-full bg-amber-500/15 px-2 py-0.5 text-xs font-medium text-amber-600 dark:text-amber-500"
    >
      {t('budget.import.duplicate')}
    </span>
  );
}

interface ImportPreviewPanelProps {
  file: File;
  preview: ImportPreview;
  checked: Record<number, boolean>;
  checkedCount: number;
  commitPending: boolean;
  commitErrorMessage: string | null;
  onToggle: (index: number, value: boolean) => void;
  onCommit: () => void;
  onDiscard: () => void;
}

function ImportPreviewPanel({
  file,
  preview,
  checked,
  checkedCount,
  commitPending,
  commitErrorMessage,
  onToggle,
  onCommit,
  onDiscard,
}: Readonly<ImportPreviewPanelProps>) {
  const { t, i18n } = useTranslation();

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm">
        <FileText className="h-4 w-4 shrink-0 text-muted-foreground" />
        <span className="truncate font-medium">{file.name}</span>
        <span className="text-muted-foreground">{formatFileSize(file.size)}</span>
        <Button
          variant="ghost"
          size="icon"
          className="ml-auto h-6 w-6"
          aria-label={t('budget.import.removeFile')}
          onClick={onDiscard}
        >
          <X className="h-4 w-4" />
        </Button>
      </div>

      <p className="text-sm text-muted-foreground">
        {t('budget.import.previewSummary', {
          new: preview.newRows,
          duplicates: preview.duplicates,
          failed: preview.failed,
        })}
      </p>

      {preview.errors.length > 0 && (
        <Collapsible>
          <CollapsibleTrigger className="inline-flex items-center gap-1 text-sm font-medium text-destructive">
            <ChevronDown className="h-4 w-4" />
            {t('budget.import.parseErrors', { count: preview.errors.length })}
          </CollapsibleTrigger>
          <CollapsibleContent>
            <ul className="mt-1 list-disc pl-6 text-xs text-destructive">
              {preview.errors.map((error) => (
                <li key={error}>{error}</li>
              ))}
            </ul>
          </CollapsibleContent>
        </Collapsible>
      )}

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-8" />
            <TableHead>{t('budget.import.colDate')}</TableHead>
            <TableHead>{t('budget.import.colDescription')}</TableHead>
            <TableHead className="text-right">{t('budget.import.colAmount')}</TableHead>
            <TableHead>{t('budget.import.colCategory')}</TableHead>
            <TableHead>{t('budget.import.colStatus')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {preview.rows.map((row) => (
            <PreviewRow
              key={row.index}
              row={row}
              checked={checked[row.index] ?? false}
              language={i18n.language}
              onToggle={(value) => onToggle(row.index, value)}
            />
          ))}
        </TableBody>
      </Table>

      {commitErrorMessage && <p className="text-sm text-destructive">{commitErrorMessage}</p>}

      <div className="flex items-center gap-2">
        <Button onClick={onCommit} disabled={checkedCount === 0 || commitPending}>
          {commitPending
            ? t('common.loading')
            : t('budget.import.importCount', { count: checkedCount })}
        </Button>
        <Button variant="outline" onClick={onDiscard}>
          {t('common.cancel')}
        </Button>
      </div>
    </div>
  );
}

interface PreviewRowProps {
  row: ImportPreviewRow;
  checked: boolean;
  language: string;
  onToggle: (value: boolean) => void;
}

function PreviewRow({ row, checked, language, onToggle }: Readonly<PreviewRowProps>) {
  const { t } = useTranslation();
  const locked = isLocked(row);

  return (
    <TableRow className={checked ? '' : 'text-muted-foreground'}>
      <TableCell>
        <Checkbox
          aria-label={`${t('budget.import.selectRow')}: ${row.description}`}
          checked={checked}
          disabled={locked}
          title={locked ? t('budget.import.duplicateLocked') : undefined}
          onCheckedChange={(value) => onToggle(value === true)}
        />
      </TableCell>
      <TableCell className="whitespace-nowrap">{formatDate(row.date, language)}</TableCell>
      <TableCell className="max-w-64 truncate">{row.description}</TableCell>
      <TableCell className={`text-right tabular-nums ${amountColour(row.amount)}`}>
        {formatCHF(row.amount)}
      </TableCell>
      <TableCell>
        {row.suggestedCategoryName ? (
          <Badge variant="secondary">{row.suggestedCategoryName}</Badge>
        ) : (
          '—'
        )}
      </TableCell>
      <TableCell>{row.duplicate && <DuplicatePill locked={locked} />}</TableCell>
    </TableRow>
  );
}

function ImportResult({
  result,
  onClose,
}: Readonly<{ result: ImportCommitResult; onClose: () => void }>) {
  const { t } = useTranslation();

  return (
    <div className="space-y-4">
      <p className="text-sm">
        {t('budget.import.resultSummary', {
          imported: result.imported,
          skipped: result.skippedDuplicates,
          failed: result.failed,
        })}
      </p>
      {result.errors.length > 0 && (
        <ul className="list-disc pl-6 text-xs text-destructive">
          {result.errors.map((error) => (
            <li key={error}>{error}</li>
          ))}
        </ul>
      )}
      <Button onClick={onClose}>{t('common.close')}</Button>
    </div>
  );
}
