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
import { budgetApi } from '@/api/budget';
import type { MonthlyBudget, MonthlyBudgetInput } from '@/api/budget';

const FIELDS = ['netIncome', 'savings', 'investing', 'pillar3a', 'taxReserve', 'workCosts'] as const;

type Field = (typeof FIELDS)[number];

interface MonthlyBudgetDialogProps {
  budget: MonthlyBudget;
  onClose: () => void;
}

/** Edit dialog for the six allocation amounts — mount fresh per open. */
export function MonthlyBudgetDialog({ budget, onClose }: Readonly<MonthlyBudgetDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [values, setValues] = useState<Record<Field, string>>(() => ({
    netIncome: String(budget.netIncome),
    savings: String(budget.savings),
    investing: String(budget.investing),
    pillar3a: String(budget.pillar3a),
    taxReserve: String(budget.taxReserve),
    workCosts: String(budget.workCosts),
  }));

  const save = useMutation({
    mutationFn: (data: MonthlyBudgetInput) => budgetApi.updateMonthlyBudget(token, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['monthly-budget'] });
      onClose();
    },
  });

  const canSubmit = FIELDS.every((field) => values[field].trim() !== '');

  const handleSave = () => {
    save.mutate({
      netIncome: Number.parseFloat(values.netIncome),
      savings: Number.parseFloat(values.savings),
      investing: Number.parseFloat(values.investing),
      pillar3a: Number.parseFloat(values.pillar3a),
      taxReserve: Number.parseFloat(values.taxReserve),
      workCosts: Number.parseFloat(values.workCosts),
    });
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('budget.monthly.editTitle')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            {FIELDS.map((field) => (
              <div key={field} className="space-y-2">
                <Label htmlFor={`monthly-budget-${field}`}>{t(`budget.monthly.${field}`)}</Label>
                <Input
                  id={`monthly-budget-${field}`}
                  type="number"
                  min={0}
                  step={0.01}
                  value={values[field]}
                  onChange={(e) => setValues((prev) => ({ ...prev, [field]: e.target.value }))}
                />
              </div>
            ))}
          </div>
          {/* Server errors, e.g. the RFC-7807 detail on validation failures. */}
          {save.error && <p className="text-sm text-destructive">{save.error.message}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleSave} disabled={!canSubmit || save.isPending}>
            {save.isPending ? t('common.loading') : t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
