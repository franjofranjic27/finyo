import { useRef, useState } from 'react';
import type { DragEvent, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Briefcase, Building, FileText, HeartPulse, Landmark, Loader2, Shield, Upload, X,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { useAuth } from '@/auth/useAuth';
import { taxApi } from '@/api/tax';
import type { TaxYearInputs, TaxYearUpsertRequest } from '@/api/tax';
import { classifyDocument, extractDocument, TaxDocumentError } from '@/api/taxDocuments';
import type { DocumentClassification, DocumentExtraction, TaxDocumentType } from '@/api/taxDocuments';
import { formatCHF } from '@/lib/formatters';

const MAX_FILE_BYTES = 10 * 1024 * 1024;

const DOCUMENT_TYPES: readonly { type: TaxDocumentType; icon: LucideIcon }[] = [
  { type: 'SALARY_CERTIFICATE', icon: Briefcase },
  { type: 'HEALTH_INSURANCE', icon: HeartPulse },
  { type: 'SECURITIES_STATEMENT', icon: Landmark },
  { type: 'PILLAR_3A', icon: Shield },
  { type: 'ASSESSMENT', icon: Building },
];

// Mirrors the CalculatorCard fallback for years without a persisted record.
const EMPTY_INPUTS: TaxYearInputs = {
  cantonCode: null,
  bfsNumber: null,
  civilStatus: null,
  churchAffiliation: null,
  numberOfChildren: null,
  grossEmploymentIncome: null,
  selfEmploymentIncome: null,
  investmentIncome: null,
  rentalIncome: null,
  deductionProfessionalExpenses: null,
  deductionInsurancePremiums: null,
  deductionCharitableDonations: null,
  deductionDebtInterest: null,
  pillar3aContribution: null,
  netWealth: null,
};

type ProcessError =
  | { kind: 'validation'; message: string }
  | { kind: 'scan'; message: string }
  | { kind: 'wrongType'; message: string; detectedType: TaxDocumentType }
  | { kind: 'generic'; message: string };

function toProcessError(error: unknown, t: (key: string) => string): ProcessError {
  if (error instanceof TaxDocumentError && error.status === 422) {
    return error.detectedType
      ? { kind: 'wrongType', message: error.message, detectedType: error.detectedType }
      : { kind: 'scan', message: error.message };
  }
  const message = error instanceof Error ? error.message : t('common.error');
  return { kind: 'generic', message };
}

function isPdf(file: File): boolean {
  return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
}

function formatFileSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

interface TaxDocumentUploadDialogProps {
  year: number;
  /** The year's current inputs — the base for the merged upsert payload. */
  inputs: TaxYearInputs | null;
  /** Current assessed amount, shown as the "current" value for assessments. */
  assessedAmount: number | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Upload dialog for tax PDFs: classifies the document, previews the extracted
 * values and merges the selected ones into the tax year. The file itself is
 * only processed in memory and never persisted.
 */
export function TaxDocumentUploadDialog({
  year,
  inputs,
  assessedAmount,
  open,
  onOpenChange,
}: Readonly<TaxDocumentUploadDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [classification, setClassification] = useState<DocumentClassification | null>(null);
  const [extraction, setExtraction] = useState<DocumentExtraction | null>(null);
  const [checked, setChecked] = useState<Record<string, boolean>>({});
  const [processError, setProcessError] = useState<ProcessError | null>(null);

  const reset = () => {
    setFile(null);
    setClassification(null);
    setExtraction(null);
    setChecked({});
    setProcessError(null);
    apply.reset();
  };

  const showExtraction = (result: DocumentExtraction) => {
    setExtraction(result);
    // Mapped rows default to checked; info-only fields have no checkbox.
    setChecked(Object.fromEntries(result.taxYearMapping.map((m) => [m.fieldName, true])));
  };

  const process = useMutation({
    mutationFn: async (selected: File) => {
      const classified = await classifyDocument(token, selected);
      const extracted =
        classified.detectedType === 'UNKNOWN'
          ? null
          : await extractDocument(token, classified.detectedType, selected);
      return { classified, extracted };
    },
    onSuccess: ({ classified, extracted }) => {
      setClassification(classified);
      if (extracted) showExtraction(extracted);
    },
    onError: (error) => setProcessError(toProcessError(error, t)),
  });

  // Manual type pick after UNKNOWN and the wrong-type shortcut re-use the file.
  const extractAs = useMutation({
    mutationFn: async ({ type, selected }: { type: TaxDocumentType; selected: File }) => {
      const extracted = await extractDocument(token, type, selected);
      return { type, extracted };
    },
    onSuccess: ({ type, extracted }) => {
      setProcessError(null);
      setClassification((previous) => ({
        detectedType: type,
        confidence: previous?.confidence ?? 1,
      }));
      showExtraction(extracted);
    },
    onError: (error) => setProcessError(toProcessError(error, t)),
  });

  const apply = useMutation({
    mutationFn: () => {
      if (!extraction) throw new Error('No extraction to apply');
      const overrides: Record<string, number> = {};
      for (const mapping of extraction.taxYearMapping) {
        if (checked[mapping.fieldName]) overrides[mapping.targetTaxYearField] = mapping.value;
      }
      const payload = { ...(inputs ?? EMPTY_INPUTS), ...overrides } as TaxYearUpsertRequest;
      return taxApi.upsertYear(token, year, payload);
    },
    onSuccess: (detail) => {
      queryClient.setQueryData(['tax', 'year', String(year)], detail);
      queryClient.invalidateQueries({ queryKey: ['tax', 'years'] });
      reset();
      onOpenChange(false);
    },
  });

  const isProcessing = process.isPending || extractAs.isPending;

  const handleFile = (selected: File) => {
    setProcessError(null);
    setClassification(null);
    setExtraction(null);
    if (!isPdf(selected)) {
      setProcessError({ kind: 'validation', message: t('tax.upload.errors.invalidFile') });
      return;
    }
    if (selected.size > MAX_FILE_BYTES) {
      setProcessError({ kind: 'validation', message: t('tax.upload.errors.tooLarge') });
      return;
    }
    setFile(selected);
    process.mutate(selected);
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    if (isProcessing) return;
    const dropped = event.dataTransfer.files?.[0];
    if (dropped) handleFile(dropped);
  };

  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen) reset();
    onOpenChange(nextOpen);
  };

  const typeLabel = (type: TaxDocumentType) => t(`tax.upload.types.${type}`);
  const needsManualType = classification?.detectedType === 'UNKNOWN' && !extraction;
  const selectedCount =
    extraction?.taxYearMapping.filter((m) => checked[m.fieldName]).length ?? 0;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{t('tax.upload.title')}</DialogTitle>
          <DialogDescription>{t('tax.upload.subtitle', { year })}</DialogDescription>
        </DialogHeader>

        {extraction && file ? (
          <ExtractionPreview
            file={file}
            year={year}
            inputs={inputs}
            assessedAmount={assessedAmount}
            classification={classification}
            extraction={extraction}
            checked={checked}
            selectedCount={selectedCount}
            applyPending={apply.isPending}
            applyErrorMessage={apply.error?.message ?? null}
            onToggle={(fieldName, value) =>
              setChecked((previous) => ({ ...previous, [fieldName]: value }))
            }
            onApply={() => apply.mutate()}
            onDiscard={reset}
          />
        ) : (
          <div className="space-y-4">
            <Dropzone
              isProcessing={isProcessing}
              onDrop={handleDrop}
              onBrowse={() => fileInputRef.current?.click()}
            />
            <input
              ref={fileInputRef}
              type="file"
              accept="application/pdf,.pdf"
              className="sr-only"
              aria-label={t('tax.upload.chooseFile')}
              onChange={(event) => {
                const selected = event.target.files?.[0];
                if (selected) handleFile(selected);
                // Allow re-selecting the same file after a fix.
                event.target.value = '';
              }}
            />
            {processError && (
              <ProcessErrorPanel
                error={processError}
                typeLabel={typeLabel}
                retryPending={extractAs.isPending}
                onProcessAs={(type) => file && extractAs.mutate({ type, selected: file })}
              />
            )}
            {needsManualType && (
              <p className="text-sm text-amber-600 dark:text-amber-500">
                {t('tax.upload.unknownType')}
              </p>
            )}
            <DocumentTypeList
              selectable={needsManualType && !isProcessing}
              onSelect={(type) => file && extractAs.mutate({ type, selected: file })}
            />
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
          <p className="text-sm text-muted-foreground">{t('tax.upload.analyzing')}</p>
        </>
      ) : (
        <>
          <Upload className="h-6 w-6 text-muted-foreground" />
          <p className="text-sm font-medium">{t('tax.upload.dropzone')}</p>
          <Button variant="outline" size="sm" onClick={onBrowse}>
            {t('tax.upload.chooseFile')}
          </Button>
          <p className="text-xs text-muted-foreground">{t('tax.upload.dropzoneMax')}</p>
        </>
      )}
    </div>
  );
}

interface DocumentTypeListProps {
  /** When true (UNKNOWN classification) the entries become pick buttons. */
  selectable: boolean;
  onSelect: (type: TaxDocumentType) => void;
}

function DocumentTypeList({ selectable, onSelect }: Readonly<DocumentTypeListProps>) {
  const { t } = useTranslation();
  return (
    <ul className="space-y-1">
      {DOCUMENT_TYPES.map(({ type, icon: Icon }) => {
        const content = (
          <>
            <Icon className="h-4 w-4 shrink-0 text-muted-foreground" />
            <span className="font-medium">{t(`tax.upload.types.${type}`)}</span>
            <span className="hidden text-muted-foreground sm:inline">
              {t(`tax.upload.hints.${type}`)}
            </span>
            {type === 'ASSESSMENT' ? (
              <Pill className="ml-auto bg-amber-500/15 text-amber-600 dark:text-amber-500">
                {t('tax.upload.digitalOnly')}
              </Pill>
            ) : (
              <Pill className="ml-auto bg-emerald-500/15 text-emerald-600 dark:text-emerald-500">
                {t('tax.upload.supported')}
              </Pill>
            )}
          </>
        );
        return (
          <li key={type}>
            {selectable ? (
              <button
                type="button"
                className="flex w-full items-center gap-2 rounded-md border px-3 py-2 text-left text-sm hover:bg-muted/50"
                onClick={() => onSelect(type)}
              >
                {content}
              </button>
            ) : (
              <div className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm">
                {content}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}

interface ProcessErrorPanelProps {
  error: ProcessError;
  typeLabel: (type: TaxDocumentType) => string;
  retryPending: boolean;
  onProcessAs: (type: TaxDocumentType) => void;
}

function ProcessErrorPanel({
  error,
  typeLabel,
  retryPending,
  onProcessAs,
}: Readonly<ProcessErrorPanelProps>) {
  const { t } = useTranslation();
  if (error.kind === 'wrongType') {
    return (
      <div className="space-y-2 rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm">
        <p className="font-semibold text-destructive">{t('tax.upload.errors.wrongTypeTitle')}</p>
        <p>{error.message}</p>
        <Button
          variant="outline"
          size="sm"
          disabled={retryPending}
          onClick={() => onProcessAs(error.detectedType)}
        >
          {t('tax.upload.processAs', { type: typeLabel(error.detectedType) })}
        </Button>
      </div>
    );
  }
  if (error.kind === 'scan') {
    return (
      <div className="rounded-md border border-amber-500/50 bg-amber-500/10 p-3 text-sm text-amber-600 dark:text-amber-500">
        {error.message}
      </div>
    );
  }
  return <p className="text-sm text-destructive">{error.message}</p>;
}

interface ExtractionPreviewProps {
  file: File;
  year: number;
  inputs: TaxYearInputs | null;
  assessedAmount: number | null;
  classification: DocumentClassification | null;
  extraction: DocumentExtraction;
  checked: Record<string, boolean>;
  selectedCount: number;
  applyPending: boolean;
  applyErrorMessage: string | null;
  onToggle: (fieldName: string, value: boolean) => void;
  onApply: () => void;
  onDiscard: () => void;
}

function ExtractionPreview({
  file,
  year,
  inputs,
  assessedAmount,
  classification,
  extraction,
  checked,
  selectedCount,
  applyPending,
  applyErrorMessage,
  onToggle,
  onApply,
  onDiscard,
}: Readonly<ExtractionPreviewProps>) {
  const { t } = useTranslation();

  const mappedFieldNames = new Set(extraction.taxYearMapping.map((m) => m.fieldName));
  const infoFields = Object.entries(extraction.fields).filter(
    ([fieldName, value]) => !mappedFieldNames.has(fieldName) && value != null,
  );

  const currentValueFor = (target: string): number | null => {
    if (target === 'assessedAmount') return assessedAmount;
    const value = inputs ? (inputs as unknown as Record<string, unknown>)[target] : null;
    return typeof value === 'number' ? value : null;
  };

  return (
    <div className="space-y-4">
      {/* File meta — the PDF is processed in memory only. */}
      <div className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm">
        <FileText className="h-4 w-4 shrink-0 text-muted-foreground" />
        <span className="truncate font-medium">{file.name}</span>
        <span className="text-muted-foreground">{formatFileSize(file.size)}</span>
        <span className="text-xs text-muted-foreground">· {t('tax.upload.notStored')}</span>
        <Button
          variant="ghost"
          size="icon"
          className="ml-auto h-6 w-6"
          aria-label={t('tax.upload.removeFile')}
          onClick={onDiscard}
        >
          <X className="h-4 w-4" />
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-2 text-sm">
        <Badge variant="secondary">{t(`tax.upload.types.${extraction.type}`)}</Badge>
        {classification && (
          <span className="text-muted-foreground">
            {t('tax.upload.confidence', { pct: Math.round(classification.confidence * 100) })}
          </span>
        )}
        {extraction.taxYear != null && (
          <span className="text-muted-foreground">
            {t('tax.upload.documentYear', { year: extraction.taxYear })}
          </span>
        )}
      </div>

      {extraction.taxYear != null && extraction.taxYear !== year && (
        <div className="rounded-md border border-amber-500/50 bg-amber-500/10 p-3 text-sm text-amber-600 dark:text-amber-500">
          {t('tax.upload.yearMismatch', { docYear: extraction.taxYear, year })}
        </div>
      )}

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-8" />
            <TableHead>{t('tax.upload.table.field')}</TableHead>
            <TableHead className="text-right">{t('tax.upload.table.extracted')}</TableHead>
            <TableHead className="text-right">{t('tax.upload.table.current')}</TableHead>
            <TableHead>{t('tax.upload.table.target')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {extraction.taxYearMapping.map((mapping) => {
            const current = currentValueFor(mapping.targetTaxYearField);
            return (
              <TableRow key={mapping.fieldName}>
                <TableCell>
                  <Checkbox
                    aria-label={mapping.label}
                    checked={checked[mapping.fieldName] ?? false}
                    onCheckedChange={(value) => onToggle(mapping.fieldName, value === true)}
                  />
                </TableCell>
                <TableCell>{mapping.label}</TableCell>
                <TableCell className="text-right font-medium">
                  {formatCHF(mapping.value)}
                </TableCell>
                <TableCell className="text-right text-muted-foreground">
                  {current != null ? formatCHF(current) : '—'}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {t(`tax.upload.targets.${mapping.targetTaxYearField}`, {
                    defaultValue: mapping.targetTaxYearField,
                  })}
                </TableCell>
              </TableRow>
            );
          })}
          {infoFields.map(([fieldName, value]) => (
            <TableRow key={fieldName} className="text-muted-foreground">
              <TableCell />
              <TableCell>
                {t(`tax.upload.fields.${fieldName}`, { defaultValue: fieldName })}
              </TableCell>
              <TableCell className="text-right">
                {typeof value === 'number' ? formatCHF(value) : value}
              </TableCell>
              <TableCell className="text-right">
                <Pill className="bg-muted text-muted-foreground">
                  {t('tax.upload.infoOnly')}
                </Pill>
              </TableCell>
              <TableCell />
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {applyErrorMessage && <p className="text-sm text-destructive">{applyErrorMessage}</p>}

      <div className="flex flex-wrap items-center gap-2">
        <Button onClick={onApply} disabled={selectedCount === 0 || applyPending}>
          {applyPending
            ? t('common.loading')
            : t('tax.upload.applyCount', { count: selectedCount })}
        </Button>
        <Button variant="outline" onClick={onDiscard}>
          {t('tax.upload.discard')}
        </Button>
        <p className="text-xs text-muted-foreground">{t('tax.upload.overwriteHint')}</p>
      </div>
    </div>
  );
}

function Pill({ className, children }: Readonly<{ className: string; children: ReactNode }>) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-xs font-medium ${className}`}
    >
      {children}
    </span>
  );
}
