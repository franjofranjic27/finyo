import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { useAuth } from '@/auth/useAuth';
import { ASSET_CLASSES } from '@/api/portfolio';
import type { AssetClass } from '@/api/portfolio';
import { wealthApi } from '@/api/wealth';
import type { WealthBucket, WealthBucketInput, WealthSource } from '@/api/wealth';

interface BucketFormDialogProps {
  /** null = create, otherwise edit. Mount the dialog fresh per open. */
  bucket: WealthBucket | null;
  onClose: () => void;
}

export function BucketFormDialog({ bucket, onClose }: Readonly<BucketFormDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [name, setName] = useState(bucket?.name ?? '');
  const [note, setNote] = useState(bucket?.note ?? '');
  const [source, setSource] = useState<WealthSource>(bucket?.source ?? 'MANUAL');
  // For manual buckets the aggregated balance IS the manual balance.
  const [manualBalance, setManualBalance] = useState(
    bucket?.source === 'MANUAL' ? String(bucket.balance) : '',
  );
  const [assetClasses, setAssetClasses] = useState<AssetClass[]>(bucket?.assetClasses ?? []);
  const [monthlyRate, setMonthlyRate] = useState(bucket ? String(bucket.monthlyRate) : '');

  const save = useMutation({
    mutationFn: (input: WealthBucketInput) =>
      bucket ? wealthApi.updateBucket(token, bucket.id, input) : wealthApi.createBucket(token, input),
    onSuccess: () => {
      // Prefix match also invalidates ['wealth', 'history'].
      queryClient.invalidateQueries({ queryKey: ['wealth'] });
      onClose();
    },
  });

  const toggleAssetClass = (assetClass: AssetClass, checked: boolean) => {
    setAssetClasses((current) =>
      checked ? [...current, assetClass] : current.filter((item) => item !== assetClass),
    );
  };

  const handleSave = () => {
    save.mutate({
      name: name.trim(),
      note: note.trim() || undefined,
      source,
      manualBalance:
        source === 'MANUAL' && manualBalance ? Number.parseFloat(manualBalance) : undefined,
      assetClasses: source === 'PORTFOLIO' ? assetClasses : undefined,
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
            <Label>{t('wealth.form.source')}</Label>
            {/* Stacked full-width buttons — the labels are too long for one row
                inside the sm:max-w-md dialog and must never overflow it. */}
            <div className="grid gap-1">
              <Button
                variant={source === 'MANUAL' ? 'default' : 'outline'}
                size="sm"
                className="w-full justify-start"
                onClick={() => setSource('MANUAL')}
              >
                {t('wealth.form.sourceManual')}
              </Button>
              <Button
                variant={source === 'PORTFOLIO' ? 'default' : 'outline'}
                size="sm"
                className="w-full justify-start"
                onClick={() => setSource('PORTFOLIO')}
              >
                {t('wealth.form.sourcePortfolio')}
              </Button>
              <Button
                variant={source === 'PILLAR3' ? 'default' : 'outline'}
                size="sm"
                className="w-full justify-start"
                onClick={() => setSource('PILLAR3')}
              >
                {t('wealth.form.sourcePillar3')}
              </Button>
            </div>
          </div>
          {source === 'MANUAL' && (
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
          )}
          {source === 'PORTFOLIO' && (
            <div className="space-y-2">
              <Label>{t('wealth.form.assetClasses')}</Label>
              <div className="grid grid-cols-2 gap-2">
                {ASSET_CLASSES.map((assetClass) => (
                  <div key={assetClass} className="flex items-center gap-2">
                    <Checkbox
                      id={`bucket-ac-${assetClass}`}
                      checked={assetClasses.includes(assetClass)}
                      onCheckedChange={(checked) => toggleAssetClass(assetClass, checked === true)}
                    />
                    <Label htmlFor={`bucket-ac-${assetClass}`} className="font-normal">
                      {t(`assetClass.${assetClass}`)}
                    </Label>
                  </div>
                ))}
              </div>
              <p className="text-xs text-muted-foreground">{t('wealth.form.assetClassesHint')}</p>
            </div>
          )}
          {source === 'PILLAR3' && (
            <p className="text-xs text-muted-foreground">{t('wealth.form.pillar3Hint')}</p>
          )}
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
          {/* Server errors, e.g. the 409 ProblemDetail message on asset-class overlaps. */}
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
