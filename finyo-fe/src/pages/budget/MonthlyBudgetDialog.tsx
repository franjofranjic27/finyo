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
import type { MonthlyBudget, MonthlyBudgetInput } from '@/api/budget';

interface MonthlyBudgetDialogProps {
  budget: MonthlyBudget;
  onClose: () => void;
}

/** Edit dialog for the net income — mount fresh per open. */
export function MonthlyBudgetDialog({ budget, onClose }: Readonly<MonthlyBudgetDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [netIncome, setNetIncome] = useState(String(budget.netIncome));

  const save = useMutation({
    mutationFn: (data: MonthlyBudgetInput) => budgetApi.updateMonthlyBudget(token, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['monthly-budget'] });
      onClose();
    },
  });

  const canSubmit = netIncome.trim() !== '';

  const handleSave = () => {
    save.mutate({ netIncome: Number.parseFloat(netIncome) });
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('budget.monthly.editTitle')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="monthly-budget-netIncome">{t('budget.monthly.netIncome')}</Label>
            <Input
              id="monthly-budget-netIncome"
              type="number"
              min={0}
              step={0.01}
              value={netIncome}
              onChange={(e) => setNetIncome(e.target.value)}
            />
            <p className="text-xs text-muted-foreground">{t('budget.monthly.netIncomeHint')}</p>
          </div>
          {/* Server errors, e.g. the RFC-7807 detail on validation failures. */}
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
