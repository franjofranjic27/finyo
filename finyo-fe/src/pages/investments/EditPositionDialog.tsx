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
import { positionDetailApi } from '@/api/positionDetail';
import type { UpdatePositionRequest } from '@/api/positionDetail';

/** Slim initial values so both the detail page and the table rows can feed the dialog. */
export interface EditPositionInitialValues {
  quantity: number;
  purchasePrice: number;
  purchaseDate: string | null;
  currentPrice: number | null;
}

interface EditPositionDialogProps {
  positionId: string;
  initial: EditPositionInitialValues;
  onClose: () => void;
}

/** Edit dialog for the holding itself (quantity, purchase data, manual price) — mount fresh per open. */
export function EditPositionDialog({
  positionId,
  initial,
  onClose,
}: Readonly<EditPositionDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const initialCurrentPrice = initial.currentPrice != null ? String(initial.currentPrice) : '';
  const [quantity, setQuantity] = useState(String(initial.quantity));
  const [purchasePrice, setPurchasePrice] = useState(String(initial.purchasePrice));
  const [purchaseDate, setPurchaseDate] = useState(initial.purchaseDate ?? '');
  const [currentPrice, setCurrentPrice] = useState(initialCurrentPrice);
  const [validationError, setValidationError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: (data: UpdatePositionRequest) =>
      positionDetailApi.updatePosition(token, positionId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['position', positionId] });
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      onClose();
    },
  });

  const handleSave = () => {
    // mirror the backend rules so invalid values never hit the API
    const quantityValue = Number.parseFloat(quantity);
    if (Number.isNaN(quantityValue) || quantityValue <= 0) {
      setValidationError(t('investments.position.editPositionDialog.quantityInvalid'));
      return;
    }
    const purchasePriceValue = Number.parseFloat(purchasePrice);
    if (Number.isNaN(purchasePriceValue) || purchasePriceValue < 0) {
      setValidationError(t('investments.position.editPositionDialog.purchasePriceInvalid'));
      return;
    }
    const currentPriceValue = currentPrice.trim() === '' ? null : Number.parseFloat(currentPrice);
    if (currentPriceValue !== null && (Number.isNaN(currentPriceValue) || currentPriceValue < 0)) {
      setValidationError(t('investments.position.editPositionDialog.currentPriceInvalid'));
      return;
    }
    setValidationError(null);
    const payload: UpdatePositionRequest = {
      quantity: quantityValue,
      purchasePrice: purchasePriceValue,
      // full field set: null is an explicit clear (backend contract)
      purchaseDate: purchaseDate || null,
    };
    // The prefilled value may be a live market price — only persist a manual
    // override when the user actually changed the field.
    if (currentPriceValue !== null && currentPrice !== initialCurrentPrice) {
      payload.currentPrice = currentPriceValue;
    }
    save.mutate(payload);
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('investments.position.editPositionDialog.title')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="position-quantity">
                {t('investments.position.editPositionDialog.quantity')}
              </Label>
              <Input
                id="position-quantity"
                type="number"
                min={0}
                step={0.000001}
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="position-purchase-price">
                {t('investments.position.editPositionDialog.purchasePrice')}
              </Label>
              <Input
                id="position-purchase-price"
                type="number"
                min={0}
                step={0.0001}
                value={purchasePrice}
                onChange={(e) => setPurchasePrice(e.target.value)}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="position-purchase-date">
              {t('investments.position.editPositionDialog.purchaseDate')}
            </Label>
            <Input
              id="position-purchase-date"
              type="date"
              value={purchaseDate}
              onChange={(e) => setPurchaseDate(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="position-current-price">
              {t('investments.position.editPositionDialog.currentPrice')}
            </Label>
            <Input
              id="position-current-price"
              type="number"
              min={0}
              step={0.0001}
              value={currentPrice}
              onChange={(e) => setCurrentPrice(e.target.value)}
            />
            <p className="text-xs text-muted-foreground">
              {t('investments.position.editPositionDialog.currentPriceHint')}
            </p>
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
            {save.isPending
              ? t('common.loading')
              : t('investments.position.editPositionDialog.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
