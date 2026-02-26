import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { budgetsApi } from '@/api/budgets';
import { apiRequest } from '@/api/client';
import { formatCHF } from '@/lib/formatters';
import type { BudgetStatus, Category } from '@/types';

function AddBudgetDialog({
  open,
  onClose,
  categories,
  token,
}: {
  open: boolean;
  onClose: () => void;
  categories: Category[];
  token: string;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [categoryId, setCategoryId] = useState('');
  const [amount, setAmount] = useState('');
  const [period, setPeriod] = useState<'MONTHLY' | 'WEEKLY'>('MONTHLY');
  const [validFrom, setValidFrom] = useState(new Date().toISOString().slice(0, 10));
  const [validUntil, setValidUntil] = useState('');

  const { mutate, isPending } = useMutation({
    mutationFn: () =>
      budgetsApi.create(token, {
        categoryId,
        amount,
        period,
        validFrom,
        validUntil: validUntil || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
      onClose();
      setCategoryId('');
      setAmount('');
      setPeriod('MONTHLY');
      setValidFrom(new Date().toISOString().slice(0, 10));
      setValidUntil('');
    },
  });

  const expenseCategories = categories.filter((c) => c.type === 'EXPENSE');

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('budget.addBudget')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label>{t('transactions.category')}</Label>
            <Select value={categoryId} onValueChange={setCategoryId}>
              <SelectTrigger><SelectValue placeholder="Select category..." /></SelectTrigger>
              <SelectContent>
                {expenseCategories.map((c) => (
                  <SelectItem key={c.id} value={c.id}>
                    {c.icon ? `${c.icon} ` : ''}{c.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>Amount (CHF)</Label>
            <Input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="500" min={1} />
          </div>
          <div className="space-y-2">
            <Label>Period</Label>
            <Select value={period} onValueChange={(v) => setPeriod(v as 'MONTHLY' | 'WEEKLY')}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="MONTHLY">Monthly</SelectItem>
                <SelectItem value="WEEKLY">Weekly</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>Valid From</Label>
              <Input type="date" value={validFrom} onChange={(e) => setValidFrom(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>Valid Until (optional)</Label>
              <Input type="date" value={validUntil} onChange={(e) => setValidUntil(e.target.value)} />
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={() => mutate()} disabled={isPending || !categoryId || !amount}>
            {isPending ? t('common.loading') : t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function BudgetCard({ budget, onDelete }: { budget: BudgetStatus; onDelete: (id: string) => void }) {
  const { t } = useTranslation();
  const pct = Math.min(budget.percentageUsed, 100);
  const over = budget.percentageUsed > 100;
  const remaining = parseFloat(budget.remaining);

  return (
    <Card className={over ? 'border-red-500/30' : ''}>
      <CardContent className="pt-4 pb-4">
        <div className="flex items-start justify-between mb-3">
          <div className="flex items-center gap-2">
            {budget.categoryColor && (
              <span
                className="h-3 w-3 rounded-full shrink-0"
                style={{ background: budget.categoryColor }}
              />
            )}
            <span className="font-medium text-sm">{budget.categoryName}</span>
            <Badge variant={over ? 'destructive' : 'secondary'} className="text-xs">
              {budget.period}
            </Badge>
          </div>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground hover:text-red-500 -mt-1"
            onClick={() => {
              if (window.confirm('Delete this budget?')) onDelete(budget.id);
            }}
          >
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        </div>
        <Progress
          value={pct}
          className={over ? '[&>div]:bg-red-500' : '[&>div]:bg-indigo-500'}
        />
        <div className="mt-2 flex justify-between text-xs text-muted-foreground">
          <span>
            {formatCHF(budget.spent)} / {formatCHF(budget.amount)}
          </span>
          <span className={over ? 'text-red-500 font-medium' : ''}>
            {over
              ? `${t('budget.overBudget')} by ${formatCHF(Math.abs(remaining))}`
              : `${formatCHF(remaining)} left`}
          </span>
        </div>
        <div className="mt-1 text-right text-xs text-muted-foreground">
          {budget.percentageUsed.toFixed(0)}% used
        </div>
      </CardContent>
    </Card>
  );
}

export function Budget() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);

  const { data: budgets, isLoading } = useQuery({
    queryKey: ['budgets'],
    queryFn: () => budgetsApi.getStatus(token),
    enabled: !!token,
  });

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: () => apiRequest<Category[]>('/categories', {}, token),
    enabled: !!token,
  });

  const { mutate: deleteBudget } = useMutation({
    mutationFn: (id: string) => budgetsApi.delete(token, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['budgets'] }),
  });

  const budgetList = budgets ?? [];
  const totalBudgeted = budgetList.reduce((s, b) => s + parseFloat(b.amount), 0);
  const totalSpent = budgetList.reduce((s, b) => s + parseFloat(b.spent), 0);
  const overCount = budgetList.filter((b) => b.percentageUsed > 100).length;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('budget.title')}</h1>
        <Button onClick={() => setAddOpen(true)} size="sm">
          <Plus className="h-4 w-4 mr-1" /> {t('budget.addBudget')}
        </Button>
      </div>

      {/* Summary row */}
      <div className="grid grid-cols-3 gap-4">
        <Card>
          <CardContent className="pt-4">
            <p className="text-xs text-muted-foreground">Total Budgeted</p>
            <p className="text-xl font-bold mt-1">{formatCHF(totalBudgeted)}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <p className="text-xs text-muted-foreground">{t('budget.spent')}</p>
            <p className="text-xl font-bold mt-1">{formatCHF(totalSpent)}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <p className="text-xs text-muted-foreground">{t('budget.overBudget')}</p>
            <p className={`text-xl font-bold mt-1 ${overCount > 0 ? 'text-red-500' : ''}`}>
              {overCount}
            </p>
          </CardContent>
        </Card>
      </div>

      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-32 w-full" />)}
        </div>
      ) : budgetList.length === 0 ? (
        <div className="py-16 text-center space-y-3">
          <p className="text-muted-foreground">{t('budget.noBudgets')}</p>
          <Button onClick={() => setAddOpen(true)}>
            <Plus className="h-4 w-4 mr-1" /> {t('budget.addBudget')}
          </Button>
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {budgetList.map((b) => (
            <BudgetCard key={b.id} budget={b} onDelete={(id) => deleteBudget(id)} />
          ))}
        </div>
      )}

      <AddBudgetDialog
        open={addOpen}
        onClose={() => setAddOpen(false)}
        categories={categories}
        token={token}
      />
    </div>
  );
}
