import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronDown, ChevronUp, FileText, RefreshCw, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { documentsApi } from '@/api/documents';
import type {
  DocumentResponse,
  DocumentStatus,
  DocumentSyncResult,
  TaxDocumentType,
} from '@/api/documents';
import { cn } from '@/lib/utils';
import { DocumentFieldsTable } from './DocumentFieldsTable';

const STATUS_CLASS: Record<DocumentStatus, string> = {
  DISCOVERED: 'bg-muted text-muted-foreground hover:bg-muted',
  ANALYZED: 'bg-muted text-muted-foreground hover:bg-muted',
  AUTO_APPLIED: 'bg-emerald-500/15 text-emerald-600 hover:bg-emerald-500/15 dark:text-emerald-500',
  NEEDS_REVIEW: 'bg-amber-500/15 text-amber-600 hover:bg-amber-500/15 dark:text-amber-500',
  APPLIED: 'bg-emerald-500/15 text-emerald-600 hover:bg-emerald-500/15 dark:text-emerald-500',
  DISMISSED: 'bg-muted text-muted-foreground hover:bg-muted',
  FAILED: 'bg-destructive/15 text-destructive hover:bg-destructive/15',
};

// Documents that still need a decision come first; settled ones sink to the bottom.
const STATUS_ORDER: Record<DocumentStatus, number> = {
  NEEDS_REVIEW: 0,
  FAILED: 1,
  DISCOVERED: 2,
  ANALYZED: 2,
  AUTO_APPLIED: 3,
  APPLIED: 4,
  DISMISSED: 5,
};

type ReviewReason =
  | { kind: 'typeMismatch'; folderType: TaxDocumentType; detectedType: TaxDocumentType }
  | { kind: 'yearMismatch'; folderYear: number; documentYear: number }
  | { kind: 'unknownType' }
  | { kind: 'valueConflict' };

function isRecognized(type: TaxDocumentType | null): type is TaxDocumentType {
  return type != null && type !== 'UNKNOWN';
}

/**
 * NEEDS_REVIEW means the import deliberately held back. The reasons are derived
 * from the document's own metadata — never from `confidence`, which is display only.
 */
function reviewReasons(doc: DocumentResponse): ReviewReason[] {
  const { detectedType, folderType, detectedTaxYear, folderTaxYear } = doc;
  const reasons: ReviewReason[] = [];

  if (isRecognized(detectedType) && isRecognized(folderType) && folderType !== detectedType) {
    reasons.push({ kind: 'typeMismatch', folderType, detectedType });
  }
  if (detectedTaxYear != null && folderTaxYear != null && detectedTaxYear !== folderTaxYear) {
    reasons.push({ kind: 'yearMismatch', folderYear: folderTaxYear, documentYear: detectedTaxYear });
  }
  if (!isRecognized(detectedType)) {
    reasons.push({ kind: 'unknownType' });
  }
  // Type and year agree, so the hold-back can only come from a value that differs
  // from what the tax year already holds.
  if (reasons.length === 0) {
    reasons.push({ kind: 'valueConflict' });
  }
  return reasons;
}

function sortForReview(documents: readonly DocumentResponse[]): DocumentResponse[] {
  return [...documents].sort(
    (a, b) =>
      STATUS_ORDER[a.status] - STATUS_ORDER[b.status] || b.createdAt.localeCompare(a.createdAt),
  );
}

function StatusBadge({ status }: Readonly<{ status: DocumentStatus }>) {
  const { t } = useTranslation();
  return (
    <Badge variant="secondary" className={cn('shrink-0', STATUS_CLASS[status])}>
      {t(`documents.status.${status}`)}
    </Badge>
  );
}

function DocumentMeta({ doc }: Readonly<{ doc: DocumentResponse }>) {
  const { t } = useTranslation();
  const typeLabel = (type: TaxDocumentType) => t(`documents.types.${type}`);

  return (
    <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
      {doc.detectedType && (
        <span>{t('documents.meta.detectedType', { type: typeLabel(doc.detectedType) })}</span>
      )}
      {doc.detectedTaxYear != null && (
        <span>{t('documents.meta.documentYear', { year: doc.detectedTaxYear })}</span>
      )}
      {doc.folderType && (
        <span>{t('documents.meta.folderType', { type: typeLabel(doc.folderType) })}</span>
      )}
      {doc.folderTaxYear != null && (
        <span>{t('documents.meta.folderYear', { year: doc.folderTaxYear })}</span>
      )}
      {/* Confidence is informational only — it never drives a decision. */}
      {doc.confidence != null && (
        <span>{t('documents.meta.confidence', { pct: Math.round(doc.confidence * 100) })}</span>
      )}
      {doc.deletedInDrive && <span>{t('documents.meta.deletedInDrive')}</span>}
    </div>
  );
}

function ReviewReasonPanel({ doc }: Readonly<{ doc: DocumentResponse }>) {
  const { t } = useTranslation();
  const typeLabel = (type: TaxDocumentType) => t(`documents.types.${type}`);

  const messages = reviewReasons(doc).map((reason) => {
    switch (reason.kind) {
      case 'typeMismatch':
        return t('documents.review.reasons.typeMismatch', {
          folderType: typeLabel(reason.folderType),
          detectedType: typeLabel(reason.detectedType),
        });
      case 'yearMismatch':
        return t('documents.review.reasons.yearMismatch', {
          folderYear: reason.folderYear,
          documentYear: reason.documentYear,
        });
      case 'unknownType':
        return t('documents.review.reasons.unknownType');
      case 'valueConflict':
        return t('documents.review.reasons.valueConflict');
    }
  });

  return (
    <div className="rounded-md border border-amber-500/50 bg-amber-500/10 p-3 text-sm text-amber-600 dark:text-amber-500">
      <p className="font-semibold">{t('documents.review.title')}</p>
      <ul className="mt-1 list-disc space-y-0.5 pl-4">
        {messages.map((message) => (
          <li key={message}>{message}</li>
        ))}
      </ul>
    </div>
  );
}

interface ApplyInput {
  id: string;
  year: number;
  fieldNames: string[];
}

interface ReviewFormProps {
  doc: DocumentResponse;
  pending: boolean;
  errorMessage: string | null;
  onApply: (input: ApplyInput) => void;
  onDismiss: (id: string) => void;
}

function ReviewForm({ doc, pending, errorMessage, onApply, onDismiss }: Readonly<ReviewFormProps>) {
  const { t } = useTranslation();
  const mappedFields = doc.extractedFields.filter((field) => field.targetTaxYearField !== null);

  const [selected, setSelected] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(mappedFields.map((field) => [field.fieldName, true])),
  );
  const [year, setYear] = useState(
    () => doc.detectedTaxYear ?? doc.folderTaxYear ?? new Date().getFullYear(),
  );

  const selectedFieldNames = mappedFields
    .map((field) => field.fieldName)
    .filter((fieldName) => selected[fieldName]);

  // An empty fieldNames list means "all mapped fields" to the backend — an empty
  // selection must therefore never be submitted.
  const canApply = selectedFieldNames.length > 0 && Number.isInteger(year) && !pending;

  const yearInputId = `document-year-${doc.id}`;

  return (
    <div className="mt-4 space-y-4 border-t border-border pt-4">
      <ReviewReasonPanel doc={doc} />

      {doc.extractedFields.length > 0 ? (
        <DocumentFieldsTable
          fields={doc.extractedFields}
          selected={selected}
          onToggle={(fieldName, checked) =>
            setSelected((previous) => ({ ...previous, [fieldName]: checked }))
          }
        />
      ) : (
        <p className="text-sm text-muted-foreground">{t('documents.review.noFields')}</p>
      )}

      <div className="flex flex-col gap-4 sm:flex-row sm:flex-wrap sm:items-end">
        <div className="space-y-1">
          <Label htmlFor={yearInputId}>{t('documents.review.year')}</Label>
          <Input
            id={yearInputId}
            type="number"
            className="w-32"
            value={year}
            onChange={(event) => setYear(Number(event.target.value))}
          />
        </div>
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
          <Button
            className="w-full sm:w-auto"
            disabled={!canApply}
            onClick={() => onApply({ id: doc.id, year, fieldNames: selectedFieldNames })}
          >
            {pending ? t('common.loading') : t('documents.review.apply')}
          </Button>
          <Button
            variant="outline"
            className="w-full sm:w-auto"
            disabled={pending}
            onClick={() => onDismiss(doc.id)}
          >
            <Trash2 className="mr-2 h-4 w-4" aria-hidden="true" />
            {t('documents.review.dismiss')}
          </Button>
        </div>
      </div>

      <p className="text-xs text-muted-foreground">{t('documents.review.yearHint')}</p>
      {errorMessage && <p className="text-sm text-destructive">{errorMessage}</p>}
    </div>
  );
}

interface DocumentCardProps {
  doc: DocumentResponse;
  pending: boolean;
  errorMessage: string | null;
  onApply: (input: ApplyInput) => void;
  onDismiss: (id: string) => void;
}

function DocumentCard({
  doc,
  pending,
  errorMessage,
  onApply,
  onDismiss,
}: Readonly<DocumentCardProps>) {
  const { t } = useTranslation();
  const needsReview = doc.status === 'NEEDS_REVIEW';
  const [expanded, setExpanded] = useState(needsReview);

  return (
    <li className="rounded-lg border border-border p-4">
      <div className="flex items-start gap-3">
        <FileText className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium">{doc.filename}</p>
          <p className="truncate text-xs text-muted-foreground">{doc.sourcePath}</p>
          <DocumentMeta doc={doc} />
        </div>
        <StatusBadge status={doc.status} />
        {needsReview && (
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 shrink-0"
            aria-expanded={expanded}
            aria-label={expanded ? t('documents.review.collapse') : t('documents.review.expand')}
            onClick={() => setExpanded((previous) => !previous)}
          >
            {expanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
          </Button>
        )}
      </div>

      {doc.status === 'FAILED' && doc.failureReason && (
        <p className="mt-3 rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
          {doc.failureReason}
        </p>
      )}

      {needsReview && expanded && (
        <ReviewForm
          doc={doc}
          pending={pending}
          errorMessage={errorMessage}
          onApply={onApply}
          onDismiss={onDismiss}
        />
      )}
    </li>
  );
}

/**
 * Inbox for tax documents synced from the cloud folder: shows what the import
 * did, why it held back, and lets the user apply or dismiss a document.
 * The page heading comes from the content-area breadcrumb.
 */
export function DocumentInboxPage() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [syncResult, setSyncResult] = useState<DocumentSyncResult | null>(null);

  const documentsQuery = useQuery({
    queryKey: ['documents'],
    queryFn: () => documentsApi.getAll(token),
    enabled: !!token,
  });

  // Applying (also automatically, during a sync) writes into a tax year, which
  // makes the cached tax-year list stale.
  const invalidateDocuments = () => {
    queryClient.invalidateQueries({ queryKey: ['documents'] });
    queryClient.invalidateQueries({ queryKey: ['tax', 'years'] });
  };

  const sync = useMutation({
    mutationFn: () => documentsApi.sync(token),
    onSuccess: (result) => {
      setSyncResult(result);
      invalidateDocuments();
    },
  });

  const apply = useMutation({
    mutationFn: ({ id, year, fieldNames }: ApplyInput) =>
      documentsApi.apply(token, id, { year, fieldNames }),
    onSuccess: invalidateDocuments,
  });

  const dismiss = useMutation({
    mutationFn: (id: string) => documentsApi.dismiss(token, id),
    onSuccess: invalidateDocuments,
  });

  const isPending = (id: string) =>
    (apply.isPending && apply.variables.id === id) ||
    (dismiss.isPending && dismiss.variables === id);

  const errorMessageFor = (id: string): string | null => {
    if (apply.isError && apply.variables.id === id) return apply.error.message;
    if (dismiss.isError && dismiss.variables === id) return dismiss.error.message;
    return null;
  };

  const documents = documentsQuery.data;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:flex-wrap sm:items-start sm:justify-between">
        <p className="max-w-2xl text-sm text-muted-foreground">{t('documents.subtitle')}</p>
        <Button className="w-full sm:w-auto" disabled={sync.isPending} onClick={() => sync.mutate()}>
          <RefreshCw
            className={cn('mr-2 h-4 w-4', sync.isPending && 'animate-spin')}
            aria-hidden="true"
          />
          {sync.isPending ? t('documents.sync.pending') : t('documents.sync.button')}
        </Button>
      </div>

      {syncResult && (
        <p className="text-sm text-muted-foreground">
          {t('documents.sync.result', {
            autoApplied: syncResult.autoApplied,
            needsReview: syncResult.needsReview,
            failed: syncResult.failed,
          })}
        </p>
      )}
      {sync.isError && <p className="text-sm text-destructive">{sync.error.message}</p>}

      {documentsQuery.isLoading && <Skeleton className="h-64 w-full" />}
      {documentsQuery.isError && (
        <p className="text-sm text-destructive">{t('documents.loadError')}</p>
      )}

      {documents?.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center gap-2 py-12 text-center">
            <p className="font-medium">{t('documents.empty.title')}</p>
            <p className="max-w-md text-sm text-muted-foreground">{t('documents.empty.text')}</p>
          </CardContent>
        </Card>
      )}

      {documents && documents.length > 0 && (
        <ul className="space-y-3">
          {sortForReview(documents).map((doc) => (
            <DocumentCard
              key={doc.id}
              doc={doc}
              pending={isPending(doc.id)}
              errorMessage={errorMessageFor(doc.id)}
              onApply={apply.mutate}
              onDismiss={dismiss.mutate}
            />
          ))}
        </ul>
      )}
    </div>
  );
}
