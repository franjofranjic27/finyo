import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, X } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useAuth } from '@/auth/useAuth';
import { budgetApi } from '@/api/budget';
import type { FixedCost, FixedCostList, PaymentInterval } from '@/api/budget';
import { formatCHF } from '@/lib/formatters';
import { FixedCostDialog } from './FixedCostDialog';

function IntervalBadge({ interval }: Readonly<{ interval: PaymentInterval }>) {
  const { t } = useTranslation();

  if (interval === 'YEARLY') {
    return (
      <Badge variant="outline" className="border-transparent bg-violet-500/10 text-violet-500">
        {t('budget.fixedCosts.intervalYearly')}
      </Badge>
    );
  }
  return <Badge variant="secondary">{t('budget.fixedCosts.intervalMonthly')}</Badge>;
}

function FixedCostRow({ item, onEdit, onDelete, deleting }: Readonly<{
  item: FixedCost;
  onEdit: (item: FixedCost) => void;
  onDelete: (item: FixedCost) => void;
  deleting: boolean;
}>) {
  const { t } = useTranslation();

  return (
    <tr className="border-b border-border">
      <td className="py-2 pr-4 font-medium">{item.name}</td>
      <td className="py-2 pr-4">
        {item.category && <Badge variant="secondary">{item.category}</Badge>}
      </td>
      <td className="py-2 pr-4">
        <IntervalBadge interval={item.paymentInterval} />
      </td>
      <td className="py-2 pr-4 text-right tabular-nums">{formatCHF(item.amountPerYear)}</td>
      <td className="py-2 pr-4 text-right font-medium tabular-nums">
        {formatCHF(item.amountPerMonth)}
      </td>
      <td className="py-2 text-right">
        <span className="inline-flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground"
            aria-label={t('budget.fixedCosts.editTitle')}
            onClick={() => onEdit(item)}
          >
            <Pencil className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground hover:text-red-500"
            aria-label={t('budget.fixedCosts.deleteLabel')}
            disabled={deleting}
            onClick={() => onDelete(item)}
          >
            <X className="h-3.5 w-3.5" />
          </Button>
        </span>
      </td>
    </tr>
  );
}

export function FixedCostsCard({ list }: Readonly<{ list: FixedCostList }>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  // `item: null` → create; `item: FixedCost` → edit; mount the dialog fresh per open.
  const [dialog, setDialog] = useState<{ item: FixedCost | null } | null>(null);

  const deleteFixedCost = useMutation({
    mutationFn: (id: string) => budgetApi.deleteFixedCost(token, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['fixed-costs'] });
      queryClient.invalidateQueries({ queryKey: ['monthly-budget'] });
    },
  });

  const confirmDelete = (item: FixedCost) => {
    if (globalThis.confirm(t('budget.fixedCosts.confirmDelete', { name: item.name }))) {
      deleteFixedCost.mutate(item.id);
    }
  };

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between space-y-0">
        <div>
          <CardTitle className="text-base">{t('budget.fixedCosts.title')}</CardTitle>
          <p className="mt-1 text-xs text-muted-foreground">{t('budget.fixedCosts.subtitle')}</p>
        </div>
        <Button variant="ghost" size="sm" onClick={() => setDialog({ item: null })}>
          <Plus className="mr-1 h-4 w-4" />
          {t('budget.fixedCosts.add')}
        </Button>
      </CardHeader>
      <CardContent>
        {list.items.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">
            {t('budget.fixedCosts.empty')}
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs text-muted-foreground">
                  <th className="py-2 pr-4 font-medium">{t('budget.fixedCosts.name')}</th>
                  <th className="py-2 pr-4 font-medium">{t('budget.fixedCosts.category')}</th>
                  <th className="py-2 pr-4 font-medium">{t('budget.fixedCosts.interval')}</th>
                  <th className="py-2 pr-4 text-right font-medium">{t('budget.fixedCosts.perYear')}</th>
                  <th className="py-2 pr-4 text-right font-medium">{t('budget.fixedCosts.perMonth')}</th>
                  <th className="py-2" aria-hidden="true" />
                </tr>
              </thead>
              <tbody>
                {list.items.map((item) => (
                  <FixedCostRow
                    key={item.id}
                    item={item}
                    onEdit={(fixedCost) => setDialog({ item: fixedCost })}
                    onDelete={confirmDelete}
                    deleting={deleteFixedCost.isPending}
                  />
                ))}
                <tr className="font-bold">
                  <td className="py-2 pr-4">{t('budget.fixedCosts.total')}</td>
                  <td className="py-2 pr-4" />
                  <td className="py-2 pr-4" />
                  <td className="py-2 pr-4 text-right tabular-nums">
                    {formatCHF(list.totalPerYear)}
                  </td>
                  <td className="py-2 pr-4 text-right tabular-nums">
                    {formatCHF(list.totalPerMonth)}
                  </td>
                  <td className="py-2" />
                </tr>
              </tbody>
            </table>
          </div>
        )}
      </CardContent>

      {dialog && <FixedCostDialog fixedCost={dialog.item} onClose={() => setDialog(null)} />}
    </Card>
  );
}
