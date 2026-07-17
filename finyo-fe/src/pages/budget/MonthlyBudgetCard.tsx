import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ChevronRight, Pencil, PiggyBank, Plus, Trash2, Wallet } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  Collapsible, CollapsibleContent, CollapsibleTrigger,
} from '@/components/ui/collapsible';
import { useAuth } from '@/auth/useAuth';
import { budgetApi } from '@/api/budget';
import type { BudgetPosition, FixedCost, FixedCostList, MonthlyBudget } from '@/api/budget';
import { formatCHF, formatPercent } from '@/lib/formatters';
import { BudgetPositionDialog } from './BudgetPositionDialog';
import { MonthlyBudgetDialog } from './MonthlyBudgetDialog';

const BASE_ROW_CLASSES = 'flex items-center gap-3 py-2.5 text-sm';
const ROW_CLASSES = `${BASE_ROW_CLASSES} border-b border-border last:border-0`;
const ICON_CHIP_CLASSES =
  'flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-secondary';

interface FixedCostGroup {
  /** `null` marks the uncategorized group, which is always rendered last. */
  category: string | null;
  items: FixedCost[];
  totalPerMonth: number;
}

/** Groups by category, sorted by monthly total descending; uncategorized last. */
function groupFixedCosts(items: FixedCost[]): FixedCostGroup[] {
  const byCategory = new Map<string | null, FixedCost[]>();
  for (const item of items) {
    const key = item.category ?? null;
    byCategory.set(key, [...(byCategory.get(key) ?? []), item]);
  }

  const groups = [...byCategory.entries()].map(([category, groupItems]) => ({
    category,
    items: groupItems,
    totalPerMonth: groupItems.reduce((sum, item) => sum + item.amountPerMonth, 0),
  }));

  return groups.sort((a, b) => {
    if (a.category === null) return 1;
    if (b.category === null) return -1;
    return b.totalPerMonth - a.totalPerMonth;
  });
}

function NetIncomeRow({ budget, onEdit }: Readonly<{ budget: MonthlyBudget; onEdit: () => void }>) {
  const { t } = useTranslation();

  return (
    <div className={ROW_CLASSES}>
      <span className={ICON_CHIP_CLASSES}>
        <Wallet className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
      </span>
      <span className="min-w-0">
        <span className="block truncate font-semibold">{t('budget.monthly.netIncome')}</span>
        <span className="block truncate text-xs text-muted-foreground">
          {t('budget.monthly.netIncomeHint')}
        </span>
      </span>
      <span className="ml-auto flex items-center gap-1">
        <span className="font-semibold tabular-nums text-emerald-500">
          +{formatCHF(budget.netIncome)}
        </span>
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-muted-foreground"
          aria-label={t('budget.monthly.edit')}
          onClick={onEdit}
        >
          <Pencil className="h-3.5 w-3.5" />
        </Button>
      </span>
    </div>
  );
}

function PositionRow({ position, onEdit, onDelete, deleting }: Readonly<{
  position: BudgetPosition;
  onEdit: (position: BudgetPosition) => void;
  onDelete: (position: BudgetPosition) => void;
  deleting: boolean;
}>) {
  const { t } = useTranslation();

  return (
    <div className={`group ${ROW_CLASSES}`}>
      <span className={ICON_CHIP_CLASSES}>
        <PiggyBank className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
      </span>
      <span className="min-w-0">
        <span className="block truncate font-semibold">{position.name}</span>
      </span>
      <span className="ml-auto flex items-center gap-1">
        <span className="font-semibold tabular-nums text-red-500">
          −{formatCHF(position.amount)}
        </span>
        {/* Touch devices have no hover — keep the actions visible below md. */}
        <span className="inline-flex items-center transition-opacity md:opacity-0 md:focus-within:opacity-100 md:group-hover:opacity-100">
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground"
            aria-label={t('budget.monthly.positionDialogTitleEdit')}
            onClick={() => onEdit(position)}
          >
            <Pencil className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground hover:text-red-500"
            aria-label={t('budget.monthly.deletePosition')}
            disabled={deleting}
            onClick={() => onDelete(position)}
          >
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        </span>
      </span>
    </div>
  );
}

function FixedCostGroupRow({ group }: Readonly<{ group: FixedCostGroup }>) {
  const { t } = useTranslation();

  return (
    <Collapsible className="border-b border-border last:border-0">
      <CollapsibleTrigger className={`group w-full text-left ${BASE_ROW_CLASSES}`}>
        <span className={ICON_CHIP_CLASSES}>
          <ChevronRight
            className="h-4 w-4 text-muted-foreground transition-transform group-data-[state=open]:rotate-90"
            aria-hidden="true"
          />
        </span>
        <span className="min-w-0">
          <span className="block truncate font-semibold">
            {group.category ?? t('budget.monthly.noCategory')}
          </span>
          <span className="block truncate text-xs text-muted-foreground">
            {t('budget.monthly.groupItems', { count: group.items.length })}
          </span>
        </span>
        <span className="ml-auto font-semibold tabular-nums text-red-500">
          −{formatCHF(group.totalPerMonth)}
        </span>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="pb-2 pl-10">
          {group.items.map((item) => (
            <div
              key={item.id}
              className="flex items-center justify-between py-1 text-xs text-muted-foreground"
            >
              <span className="min-w-0 truncate">{item.name}</span>
              <span className="tabular-nums">−{formatCHF(item.amountPerMonth)}</span>
            </div>
          ))}
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}

function AvailableTile({ budget }: Readonly<{ budget: MonthlyBudget }>) {
  const { t } = useTranslation();
  const negative = budget.available < 0;

  return (
    <div
      className={`mt-4 rounded-lg border p-4 text-center ${
        negative ? 'border-red-500/35 bg-red-500/10' : 'border-emerald-500/35 bg-emerald-500/10'
      }`}
    >
      <p
        className={`text-xs font-semibold uppercase tracking-wider ${
          negative ? 'text-red-600' : 'text-emerald-600'
        }`}
      >
        {t('budget.monthly.available')}
      </p>
      <p
        className={`mt-1 text-3xl font-bold tabular-nums ${
          negative ? 'text-red-500' : 'text-emerald-500'
        }`}
      >
        {formatCHF(budget.available)}
      </p>
      {budget.netIncome !== 0 && (
        <p className="mt-1 text-xs text-muted-foreground">
          {t('budget.monthly.availableHint', {
            pct: formatPercent((budget.available / budget.netIncome) * 100),
          })}
        </p>
      )}
    </div>
  );
}

export function MonthlyBudgetCard({ budget, fixedCosts }: Readonly<{
  budget: MonthlyBudget;
  fixedCosts: FixedCostList;
}>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [editingNetIncome, setEditingNetIncome] = useState(false);
  // `position: null` → create; `position: BudgetPosition` → edit; mount fresh per open.
  const [positionDialog, setPositionDialog] = useState<{ position: BudgetPosition | null } | null>(
    null,
  );

  const deletePosition = useMutation({
    mutationFn: (id: string) => budgetApi.deleteBudgetPosition(token, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['monthly-budget'] });
    },
  });

  const confirmDelete = (position: BudgetPosition) => {
    if (globalThis.confirm(t('budget.monthly.confirmDeletePosition', { name: position.name }))) {
      deletePosition.mutate(position.id);
    }
  };

  return (
    <Card>
      <CardHeader className="flex flex-col gap-3 space-y-0 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <CardTitle className="text-base">{t('budget.monthly.title')}</CardTitle>
          <p className="mt-1 text-xs text-muted-foreground">{t('budget.monthly.subtitle')}</p>
        </div>
        <Button variant="ghost" size="sm" onClick={() => setPositionDialog({ position: null })}>
          <Plus className="mr-1 h-4 w-4" />
          {t('budget.monthly.addPosition')}
        </Button>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col">
          <NetIncomeRow budget={budget} onEdit={() => setEditingNetIncome(true)} />
          {budget.positions.length === 0 ? (
            <p className="border-b border-border py-3 text-center text-xs text-muted-foreground last:border-0">
              {t('budget.monthly.noPositions')}
            </p>
          ) : (
            budget.positions.map((position) => (
              <PositionRow
                key={position.id}
                position={position}
                onEdit={(selected) => setPositionDialog({ position: selected })}
                onDelete={confirmDelete}
                deleting={deletePosition.isPending}
              />
            ))
          )}
          {groupFixedCosts(fixedCosts.items).map((group) => (
            <FixedCostGroupRow key={group.category ?? '__uncategorized__'} group={group} />
          ))}
        </div>
        <AvailableTile budget={budget} />
      </CardContent>

      {editingNetIncome && (
        <MonthlyBudgetDialog budget={budget} onClose={() => setEditingNetIncome(false)} />
      )}
      {positionDialog && (
        <BudgetPositionDialog
          position={positionDialog.position}
          onClose={() => setPositionDialog(null)}
        />
      )}
    </Card>
  );
}
