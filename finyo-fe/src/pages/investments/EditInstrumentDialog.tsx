import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { useAuth } from '@/auth/useAuth';
import { ASSET_CLASSES } from '@/api/portfolio';
import type { AssetClass } from '@/api/portfolio';
import { positionDetailApi } from '@/api/positionDetail';
import type { PositionDetail, UpdateInstrumentRequest } from '@/api/positionDetail';

interface EditInstrumentDialogProps {
  position: PositionDetail;
  onClose: () => void;
}

/** Small edit dialog for the instrument master data — mount fresh per open. */
export function EditInstrumentDialog({ position, onClose }: Readonly<EditInstrumentDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [name, setName] = useState(position.name);
  const [assetClass, setAssetClass] = useState<AssetClass>(position.assetClass);
  const [valor, setValor] = useState(position.valor ?? '');
  const [ter, setTer] = useState(position.ter != null ? String(position.ter) : '');
  const [factsheetUrl, setFactsheetUrl] = useState(position.factsheetUrl ?? '');
  const [validationError, setValidationError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: (data: UpdateInstrumentRequest) =>
      positionDetailApi.updateInstrument(token, position.positionId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['position', position.positionId] });
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      onClose();
    },
  });

  const handleSave = () => {
    if (!name.trim()) {
      setValidationError(t('investments.position.editDialog.nameRequired'));
      return;
    }
    const terValue = ter.trim() === '' ? null : Number.parseFloat(ter);
    // mirror the backend bounds (0–10 %) so invalid values never hit the API
    if (terValue !== null && (Number.isNaN(terValue) || terValue < 0 || terValue > 10)) {
      setValidationError(t('investments.position.editDialog.terInvalid'));
      return;
    }
    setValidationError(null);
    save.mutate({
      name: name.trim(),
      assetClass,
      valor: valor.trim() || null,
      ter: terValue,
      factsheetUrl: factsheetUrl.trim() || null,
    });
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('investments.position.editDialog.title')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="instrument-name">{t('investments.position.editDialog.name')}</Label>
            <Input
              id="instrument-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="instrument-asset-class">
              {t('investments.position.editDialog.assetClass')}
            </Label>
            <Select value={assetClass} onValueChange={(value) => setAssetClass(value as AssetClass)}>
              <SelectTrigger id="instrument-asset-class">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ASSET_CLASSES.map((value) => (
                  <SelectItem key={value} value={value}>
                    {t(`assetClass.${value}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="instrument-valor">{t('investments.position.editDialog.valor')}</Label>
              <Input
                id="instrument-valor"
                value={valor}
                onChange={(e) => setValor(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="instrument-ter">{t('investments.position.editDialog.ter')}</Label>
              <Input
                id="instrument-ter"
                type="number"
                min={0}
                max={10}
                step={0.01}
                value={ter}
                onChange={(e) => setTer(e.target.value)}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="instrument-factsheet-url">
              {t('investments.position.editDialog.factsheetUrl')}
            </Label>
            <Input
              id="instrument-factsheet-url"
              type="url"
              value={factsheetUrl}
              onChange={(e) => setFactsheetUrl(e.target.value)}
            />
          </div>
          {validationError && <p className="text-sm text-destructive">{validationError}</p>}
          {/* Server errors, e.g. the RFC-7807 detail on validation failures. */}
          {save.error && <p className="text-sm text-destructive">{save.error.message}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleSave} disabled={save.isPending}>
            {save.isPending ? t('common.loading') : t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
