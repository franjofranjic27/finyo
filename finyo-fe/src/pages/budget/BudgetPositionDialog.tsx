import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { SaveDialogFooter } from '@/components/SaveDialogFooter';
import { useAuth } from '@/auth/useAuth';
import { budgetApi } from '@/api/budget';
import type { BudgetPosition, BudgetPositionInput } from '@/api/budget';

interface BudgetPositionDialogProps {
  /** `null` creates a new position, otherwise the given one is edited. */
  position: BudgetPosition | null;
  onClose: () => void;
}

/** Create/edit dialog for a budget position — mount fresh per open. */
export function BudgetPositionDialog({ position, onClose }: Readonly<BudgetPositionDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [name, setName] = useState(position?.name ?? '');
  const [amount, setAmount] = useState(position ? String(position.amount) : '');

  const save = useMutation({
    mutationFn: (data: BudgetPositionInput) =>
      position
        ? budgetApi.updateBudgetPosition(token, position.id, data)
        : budgetApi.createBudgetPosition(token, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['monthly-budget'] });
      onClose();
    },
  });

  const canSubmit = Boolean(name.trim()) && Boolean(amount);

  const handleSave = () => {
    save.mutate({
      name: name.trim(),
      amount: Number.parseFloat(amount),
    });
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {position
              ? t('budget.monthly.positionDialogTitleEdit')
              : t('budget.monthly.positionDialogTitleAdd')}
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="budget-position-name">{t('budget.monthly.positionName')}</Label>
            <Input
              id="budget-position-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="budget-position-amount">{t('budget.monthly.positionAmount')}</Label>
            <Input
              id="budget-position-amount"
              type="number"
              min={0}
              step={0.01}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </div>
          {/* Server errors, e.g. the RFC-7807 detail on duplicate names. */}
          {save.error && <p className="text-sm text-destructive">{save.error.message}</p>}
        </div>
        <SaveDialogFooter
          onClose={onClose}
          onSave={handleSave}
          canSubmit={canSubmit}
          pending={save.isPending}
        />
      </DialogContent>
    </Dialog>
  );
}
