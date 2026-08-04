import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { useAuth } from '@/auth/useAuth';
import { wealthApi } from '@/api/wealth';
import type { WealthBucket, WealthBucketInput } from '@/api/wealth';

interface BucketFormDialogProps {
  /** null = create, otherwise edit (manual pots only — auto rows are read-only). */
  bucket: WealthBucket | null;
  onClose: () => void;
}

/** Creates or edits a manual pot; portfolio and pillar 3a rows appear automatically. */
export function BucketFormDialog({ bucket, onClose }: Readonly<BucketFormDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [name, setName] = useState(bucket?.name ?? '');
  const [note, setNote] = useState(bucket?.note ?? '');
  // For manual pots the aggregated balance IS the manual balance.
  const [manualBalance, setManualBalance] = useState(bucket ? String(bucket.balance) : '');
  const [monthlyRate, setMonthlyRate] = useState(
    bucket?.monthlyRate != null ? String(bucket.monthlyRate) : '',
  );

  const save = useMutation({
    mutationFn: (input: WealthBucketInput) =>
      bucket ? wealthApi.updateBucket(token, bucket.id, input) : wealthApi.createBucket(token, input),
    onSuccess: () => {
      // Prefix match also invalidates ['wealth', 'history'].
      queryClient.invalidateQueries({ queryKey: ['wealth'] });
      onClose();
    },
  });

  const handleSave = () => {
    save.mutate({
      name: name.trim(),
      note: note.trim() || undefined,
      source: 'MANUAL',
      manualBalance: manualBalance ? Number.parseFloat(manualBalance) : undefined,
      monthlyRate: monthlyRate ? Number.parseFloat(monthlyRate) : undefined,
      sortOrder: bucket?.sortOrder,
    });
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {bucket ? t('wealth.form.editTitle') : t('wealth.form.createTitle')}
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="bucket-name">{t('wealth.form.name')}</Label>
            <Input id="bucket-name" value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="bucket-note">{t('wealth.form.note')}</Label>
            <Input id="bucket-note" value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="bucket-balance">{t('wealth.form.manualBalance')}</Label>
            <Input
              id="bucket-balance"
              type="number"
              min={0}
              value={manualBalance}
              onChange={(e) => setManualBalance(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="bucket-monthly-rate">{t('wealth.form.monthlyRate')}</Label>
            <Input
              id="bucket-monthly-rate"
              type="number"
              min={0}
              value={monthlyRate}
              onChange={(e) => setMonthlyRate(e.target.value)}
            />
          </div>
          {/* Server errors, e.g. the ProblemDetail message on duplicate names. */}
          {save.error && <p className="text-sm text-destructive">{save.error.message}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleSave} disabled={!name.trim() || save.isPending}>
            {save.isPending
              ? t('common.loading')
              : t(bucket ? 'wealth.form.submitEdit' : 'wealth.form.submitCreate')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
