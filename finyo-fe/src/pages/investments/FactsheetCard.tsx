import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ExternalLink, FileText, Upload, X } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/auth/useAuth';
import { positionDetailApi } from '@/api/positionDetail';
import type { PositionDetail } from '@/api/positionDetail';
import { formatDate, formatFileSize } from '@/lib/formatters';
import { cn } from '@/lib/utils';

const MAX_FACTSHEET_BYTES = 10 * 1024 * 1024;
const PDF_MIME_TYPE = 'application/pdf';

export function FactsheetCard({ position }: Readonly<{ position: PositionDetail }>) {
  const { t, i18n } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [fileError, setFileError] = useState<string | null>(null);

  const invalidatePosition = () =>
    queryClient.invalidateQueries({ queryKey: ['position', position.positionId] });

  const upload = useMutation({
    mutationFn: (file: File) =>
      positionDetailApi.uploadFactsheet(token, position.positionId, file),
    onSuccess: invalidatePosition,
  });

  const remove = useMutation({
    mutationFn: () => positionDetailApi.deleteFactsheet(token, position.positionId),
    onSuccess: invalidatePosition,
  });

  const view = useMutation({
    mutationFn: () => positionDetailApi.fetchFactsheet(token, position.positionId),
    onSuccess: (blob) => {
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener');
      // revoke after the new tab had ample time to load — object URLs leak otherwise
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    },
  });

  const handleFile = (file: File | undefined) => {
    if (!file) return;
    if (file.type !== PDF_MIME_TYPE) {
      setFileError(t('investments.position.factsheet.invalidType'));
      return;
    }
    if (file.size > MAX_FACTSHEET_BYTES) {
      setFileError(t('investments.position.factsheet.tooLarge'));
      return;
    }
    setFileError(null);
    upload.mutate(file);
  };

  const mutationError = upload.error ?? remove.error ?? view.error;
  const hasFile = position.factsheet !== null;
  // uploaded PDF and external link are independent — show both when both exist
  const hasLink = position.factsheetUrl !== null;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('investments.position.factsheet.title')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <input
          ref={fileInputRef}
          id="factsheet-file"
          type="file"
          accept={PDF_MIME_TYPE}
          className="hidden"
          aria-label={t('investments.position.factsheet.dropzone')}
          onChange={(event) => {
            handleFile(event.target.files?.[0]);
            event.target.value = '';
          }}
        />

        {hasFile && position.factsheet && (
          <div className="flex items-center gap-3 rounded-md border p-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-secondary">
              <FileText className="h-5 w-5 text-muted-foreground" aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{position.factsheet.filename}</p>
              <p className="text-xs text-muted-foreground">
                {formatFileSize(position.factsheet.sizeBytes)} ·{' '}
                {formatDate(position.factsheet.uploadedAt, i18n.language)}
              </p>
            </div>
            <div className="flex shrink-0 items-center gap-1">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => view.mutate()}
                disabled={view.isPending}
              >
                {t('investments.position.factsheet.view')}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => fileInputRef.current?.click()}
                disabled={upload.isPending}
              >
                {t('investments.position.factsheet.replace')}
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7 text-muted-foreground hover:text-red-500"
                aria-label={t('investments.position.factsheet.delete')}
                onClick={() => remove.mutate()}
                disabled={remove.isPending}
              >
                <X className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>
        )}

        {hasLink && (
          <div className="flex items-center gap-3 rounded-md border p-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-secondary">
              <ExternalLink className="h-5 w-5 text-muted-foreground" aria-hidden="true" />
            </span>
            <p className="min-w-0 flex-1 truncate text-sm">{position.factsheetUrl}</p>
            <Button variant="ghost" size="sm" asChild>
              <a href={position.factsheetUrl ?? '#'} target="_blank" rel="noopener noreferrer">
                {t('investments.position.factsheet.openLink')}
              </a>
            </Button>
          </div>
        )}

        {!hasFile && (
          <button
            type="button"
            className={cn(
              'flex w-full flex-col items-center justify-center gap-2 rounded-md border-2 border-dashed p-8 text-sm text-muted-foreground transition-colors hover:border-primary/50 hover:text-foreground',
              dragOver && 'border-primary/50 bg-secondary/50',
            )}
            onClick={() => fileInputRef.current?.click()}
            onDragOver={(event) => {
              event.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={() => setDragOver(false)}
            onDrop={(event) => {
              event.preventDefault();
              setDragOver(false);
              handleFile(event.dataTransfer.files?.[0]);
            }}
            disabled={upload.isPending}
          >
            <Upload className="h-6 w-6" aria-hidden="true" />
            {upload.isPending
              ? t('investments.position.factsheet.uploading')
              : t('investments.position.factsheet.dropzone')}
          </button>
        )}

        {fileError && <p className="text-sm text-destructive">{fileError}</p>}
        {mutationError && <p className="text-sm text-destructive">{mutationError.message}</p>}
      </CardContent>
    </Card>
  );
}
