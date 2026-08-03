import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Combobox } from '@/components/ui/combobox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { useAuth } from '@/auth/useAuth';
import { budgetApi } from '@/api/budget';
import type { FixedCost, FixedCostInput, PaymentInterval } from '@/api/budget';

interface FixedCostDialogProps {
  /** `null` creates a new fixed cost, otherwise the given one is edited. */
  fixedCost: FixedCost | null;
  /** Distinct existing category values, offered as combobox suggestions. */
  categories: readonly string[];
  onClose: () => void;
}

/** Truncates free-typed amounts to two decimals (CHF has no smaller unit). */
function clampToTwoDecimals(raw: string): string {
  const [whole, decimals] = raw.split('.');
  return decimals && decimals.length > 2 ? `${whole}.${decimals.slice(0, 2)}` : raw;
}

/** Create/edit dialog for a fixed cost — mount fresh per open. */
export function FixedCostDialog({ fixedCost, categories, onClose }: Readonly<FixedCostDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [name, setName] = useState(fixedCost?.name ?? '');
  const [category, setCategory] = useState(fixedCost?.category ?? '');
  const [paymentInterval, setPaymentInterval] = useState<PaymentInterval>(
    fixedCost?.paymentInterval ?? 'MONTHLY',
  );
  const [amount, setAmount] = useState(fixedCost ? String(fixedCost.amount) : '');

  const save = useMutation({
    mutationFn: (data: FixedCostInput) =>
      fixedCost
        ? budgetApi.updateFixedCost(token, fixedCost.id, data)
        : budgetApi.createFixedCost(token, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['fixed-costs'] });
      // The monthly budget derives its fixed-costs row from this table.
      queryClient.invalidateQueries({ queryKey: ['monthly-budget'] });
      onClose();
    },
  });

  const canSubmit = Boolean(name.trim()) && Boolean(amount);

  const handleSave = () => {
    save.mutate({
      name: name.trim(),
      category: category.trim() || undefined,
      paymentInterval,
      amount: Number.parseFloat(amount),
    });
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {fixedCost ? t('budget.fixedCosts.editTitle') : t('budget.fixedCosts.createTitle')}
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="fixed-cost-name">{t('budget.fixedCosts.name')}</Label>
            <Input
              id="fixed-cost-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="fixed-cost-category">{t('budget.fixedCosts.category')}</Label>
            <Combobox
              id="fixed-cost-category"
              value={category}
              onValueChange={setCategory}
              options={categories}
              formatCreateLabel={(typedValue) => t('common.createOption', { value: typedValue })}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="fixed-cost-interval">{t('budget.fixedCosts.interval')}</Label>
              <Select
                value={paymentInterval}
                onValueChange={(value) => setPaymentInterval(value as PaymentInterval)}
              >
                <SelectTrigger id="fixed-cost-interval">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="MONTHLY">{t('budget.fixedCosts.intervalMonthly')}</SelectItem>
                  <SelectItem value="YEARLY">{t('budget.fixedCosts.intervalYearly')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="fixed-cost-amount">{t('budget.fixedCosts.amount')}</Label>
              {/* CHF prefix inside the field; the right edge keeps the number spinner free */}
              <div className="relative">
                <span
                  aria-hidden="true"
                  className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground"
                >
                  CHF
                </span>
                <Input
                  id="fixed-cost-amount"
                  type="number"
                  min={0}
                  step={0.01}
                  className="pl-12"
                  value={amount}
                  onChange={(e) => setAmount(clampToTwoDecimals(e.target.value))}
                />
              </div>
              <p className="text-xs text-muted-foreground">{t('budget.fixedCosts.amountHint')}</p>
            </div>
          </div>
          {/* Server errors, e.g. the RFC-7807 detail on validation failures. */}
          {save.error && <p className="text-sm text-destructive">{save.error.message}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleSave} disabled={!canSubmit || save.isPending}>
            {save.isPending
              ? t('common.loading')
              : t(fixedCost ? 'budget.fixedCosts.submitEdit' : 'budget.fixedCosts.submitCreate')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
