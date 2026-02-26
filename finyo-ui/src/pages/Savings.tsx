import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Archive, Trash2, ChevronDown, ChevronUp } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { Progress } from '@/components/ui/progress';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { savingsApi } from '@/api/savings';
import { formatCHF, formatDate } from '@/lib/formatters';
import type { SavingsGoal } from '@/types';

function GoalCard({
  goal,
  lang,
  onArchive,
  onDelete,
}: {
  goal: SavingsGoal;
  lang: string;
  onArchive: (id: string) => void;
  onDelete: (id: string) => void;
}) {
  const { t } = useTranslation();
  const pct = Math.min(goal.progressPercentage, 100);

  return (
    <Card>
      <CardContent className="pt-4 pb-4">
        <div className="flex items-start justify-between mb-1">
          <div className="flex items-center gap-2">
            {goal.icon && <span className="text-xl">{goal.icon}</span>}
            <div>
              <p className="font-semibold text-sm leading-snug">{goal.name}</p>
              {goal.targetDate && (
                <p className="text-xs text-muted-foreground">
                  Target: {formatDate(goal.targetDate, lang)}
                </p>
              )}
            </div>
          </div>
          <div className="flex gap-1">
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7 text-muted-foreground hover:text-amber-500"
              title="Archive"
              onClick={() => onArchive(goal.id)}
            >
              <Archive className="h-3.5 w-3.5" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7 text-muted-foreground hover:text-red-500"
              title={t('common.delete')}
              onClick={() => {
                if (window.confirm(`Delete "${goal.name}"?`)) onDelete(goal.id);
              }}
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>

        <Progress value={pct} className="[&>div]:bg-emerald-500 mt-3" />

        <div className="mt-2 flex justify-between text-xs">
          <span className="text-muted-foreground">
            {formatCHF(goal.currentAmount)} / {formatCHF(goal.targetAmount)}
          </span>
          <span className="font-medium text-emerald-500">{goal.progressPercentage.toFixed(0)}%</span>
        </div>

        {goal.monthlyRequired && (
          <p className="mt-2 text-xs text-muted-foreground">
            {t('savings.monthlyRequired')}: <span className="font-medium">{formatCHF(goal.monthlyRequired)}</span>
          </p>
        )}
      </CardContent>
    </Card>
  );
}

function AddGoalDialog({
  open,
  onClose,
  token,
}: {
  open: boolean;
  onClose: () => void;
  token: string;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [targetAmount, setTargetAmount] = useState('');
  const [currentAmount, setCurrentAmount] = useState('0');
  const [targetDate, setTargetDate] = useState('');
  const [icon, setIcon] = useState('');
  const [color, setColor] = useState('');

  const { mutate, isPending } = useMutation({
    mutationFn: () =>
      savingsApi.create(token, {
        name,
        targetAmount,
        currentAmount: currentAmount || '0',
        targetDate: targetDate || undefined,
        icon: icon || undefined,
        color: color || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['savings-goals'] });
      onClose();
      setName('');
      setTargetAmount('');
      setCurrentAmount('0');
      setTargetDate('');
      setIcon('');
      setColor('');
    },
  });

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('savings.addGoal')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label>Name *</Label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Emergency Fund" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>{t('savings.targetAmount')} (CHF) *</Label>
              <Input type="number" value={targetAmount} onChange={(e) => setTargetAmount(e.target.value)} placeholder="10000" />
            </div>
            <div className="space-y-2">
              <Label>{t('savings.currentAmount')} (CHF)</Label>
              <Input type="number" value={currentAmount} onChange={(e) => setCurrentAmount(e.target.value)} placeholder="0" />
            </div>
          </div>
          <div className="space-y-2">
            <Label>{t('savings.targetDate')} (optional)</Label>
            <Input type="date" value={targetDate} onChange={(e) => setTargetDate(e.target.value)} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>Icon (emoji, optional)</Label>
              <Input value={icon} onChange={(e) => setIcon(e.target.value)} placeholder="🏠" />
            </div>
            <div className="space-y-2">
              <Label>Color (hex, optional)</Label>
              <Input value={color} onChange={(e) => setColor(e.target.value)} placeholder="#10b981" />
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={() => mutate()} disabled={isPending || !name || !targetAmount}>
            {isPending ? t('common.loading') : t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function Savings() {
  const { t, i18n } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);
  const [showArchived, setShowArchived] = useState(false);

  const { data: goals, isLoading } = useQuery({
    queryKey: ['savings-goals'],
    queryFn: () => savingsApi.getAll(token),
    enabled: !!token,
  });

  const { mutate: archiveGoal } = useMutation({
    mutationFn: (id: string) => savingsApi.archive(token, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['savings-goals'] }),
  });

  const { mutate: deleteGoal } = useMutation({
    mutationFn: (id: string) => savingsApi.delete(token, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['savings-goals'] }),
  });

  const allGoals = goals ?? [];
  const active = allGoals.filter((g) => !g.archived);
  const archived = allGoals.filter((g) => g.archived);

  const totalSaved = active.reduce((s, g) => s + parseFloat(g.currentAmount), 0);
  const totalTarget = active.reduce((s, g) => s + parseFloat(g.targetAmount), 0);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('savings.title')}</h1>
        <Button onClick={() => setAddOpen(true)} size="sm">
          <Plus className="h-4 w-4 mr-1" /> {t('savings.addGoal')}
        </Button>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-3 gap-4">
        <Card>
          <CardContent className="pt-4">
            <p className="text-xs text-muted-foreground">Active Goals</p>
            <p className="text-xl font-bold mt-1">{active.length}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <p className="text-xs text-muted-foreground">Total Saved</p>
            <p className="text-xl font-bold mt-1 text-emerald-500">{formatCHF(totalSaved)}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <p className="text-xs text-muted-foreground">Total Target</p>
            <p className="text-xl font-bold mt-1">{formatCHF(totalTarget)}</p>
          </CardContent>
        </Card>
      </div>

      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-40 w-full" />)}
        </div>
      ) : active.length === 0 ? (
        <div className="py-16 text-center space-y-3">
          <p className="text-muted-foreground">{t('savings.noGoals')}</p>
          <Button onClick={() => setAddOpen(true)}>
            <Plus className="h-4 w-4 mr-1" /> {t('savings.addGoal')}
          </Button>
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {active.map((goal) => (
            <GoalCard
              key={goal.id}
              goal={goal}
              lang={i18n.language}
              onArchive={(id) => archiveGoal(id)}
              onDelete={(id) => deleteGoal(id)}
            />
          ))}
        </div>
      )}

      {/* Archived section */}
      {archived.length > 0 && (
        <div>
          <button
            type="button"
            className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground mb-3"
            onClick={() => setShowArchived(!showArchived)}
          >
            {showArchived ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
            Archived Goals ({archived.length})
          </button>
          {showArchived && (
            <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3 opacity-60">
              {archived.map((goal) => (
                <Card key={goal.id}>
                  <CardContent className="pt-3 pb-3">
                    <div className="flex justify-between items-center">
                      <div className="flex items-center gap-2">
                        {goal.icon && <span>{goal.icon}</span>}
                        <span className="text-sm font-medium line-through">{goal.name}</span>
                      </div>
                      <div className="flex gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-xs h-7"
                          onClick={() => archiveGoal(goal.id)}
                        >
                          Unarchive
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-7 w-7 text-muted-foreground hover:text-red-500"
                          onClick={() => {
                            if (window.confirm(`Delete "${goal.name}"?`)) deleteGoal(goal.id);
                          }}
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    </div>
                    <p className="text-xs text-muted-foreground mt-1">
                      {formatCHF(goal.currentAmount)} / {formatCHF(goal.targetAmount)}
                    </p>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </div>
      )}

      <AddGoalDialog open={addOpen} onClose={() => setAddOpen(false)} token={token} />
    </div>
  );
}
