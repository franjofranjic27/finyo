import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CalendarClock, Plus, Trash2 } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { useAuth } from '@/auth/useAuth';
import { taxApi } from '@/api/tax';
import type { TaxDeadline, TaxYearDetail } from '@/api/tax';
import { formatCHF, formatDate } from '@/lib/formatters';
import { cn } from '@/lib/utils';
import { toIsoDate } from './taxYearUtils';

interface DeadlinesPaymentsSectionProps {
  year: number;
  detail: TaxYearDetail;
  className?: string;
}

export function DeadlinesPaymentsSection({ year, detail, className }: Readonly<DeadlinesPaymentsSectionProps>) {
  const { t, i18n } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [paymentDate, setPaymentDate] = useState(toIsoDate(new Date()));
  const [amount, setAmount] = useState('');
  const [label, setLabel] = useState('');

  const [deadlineDialogOpen, setDeadlineDialogOpen] = useState(false);
  const [deadlineDate, setDeadlineDate] = useState(toIsoDate(new Date()));
  const [deadlineLabel, setDeadlineLabel] = useState('');
  const [deadlineAmount, setDeadlineAmount] = useState('');

  const invalidateYear = () => {
    queryClient.invalidateQueries({ queryKey: ['tax', 'year', String(year)] });
    queryClient.invalidateQueries({ queryKey: ['tax', 'years'] });
  };

  const toggleDeadline = useMutation({
    mutationFn: (deadline: TaxDeadline) =>
      taxApi.updateDeadline(token, year, deadline.id, {
        dueDate: deadline.dueDate,
        label: deadline.label,
        amount: deadline.amount,
        done: !deadline.done,
      }),
    onSuccess: invalidateYear,
  });

  const addDeadline = useMutation({
    mutationFn: () =>
      taxApi.addDeadline(token, year, {
        dueDate: deadlineDate,
        label: deadlineLabel,
        amount: deadlineAmount ? Number.parseFloat(deadlineAmount) : null,
        done: false,
      }),
    onSuccess: () => {
      invalidateYear();
      setDeadlineDialogOpen(false);
      setDeadlineLabel('');
      setDeadlineAmount('');
    },
  });

  const addPayment = useMutation({
    mutationFn: () =>
      taxApi.addPayment(token, year, {
        paymentDate,
        amount: Number.parseFloat(amount) || 0,
        label,
      }),
    onSuccess: () => {
      invalidateYear();
      setDialogOpen(false);
      setAmount('');
      setLabel('');
    },
  });

  const deletePayment = useMutation({
    mutationFn: (paymentId: string) => taxApi.deletePayment(token, year, paymentId),
    onSuccess: invalidateYear,
  });

  const confirmDeletePayment = (paymentId: string) => {
    if (globalThis.confirm(t('tax.confirmDeletePayment'))) {
      deletePayment.mutate(paymentId);
    }
  };

  return (
    <div className={cn('grid gap-4 md:grid-cols-2', className)}>
      {/* Deadlines */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0">
          <CardTitle className="text-base">{t('tax.deadlines')}</CardTitle>
          {/* sm+: compact header button; mobile: full-width button below the list. */}
          <Button
            variant="outline"
            size="sm"
            className="hidden sm:inline-flex"
            onClick={() => setDeadlineDialogOpen(true)}
          >
            <Plus className="mr-2 h-4 w-4" />
            {t('tax.addDeadline')}
          </Button>
        </CardHeader>
        <CardContent className="space-y-3">
          {detail.deadlines.length === 0 && (
            <p className="text-sm text-muted-foreground">{t('tax.noDeadlines')}</p>
          )}
          {detail.deadlines.map((deadline) => (
            <div key={deadline.id} className="flex items-center gap-3 text-sm">
              <CalendarClock className="h-4 w-4 shrink-0 text-muted-foreground" />
              <div className="min-w-0 flex-1">
                <p className="truncate">{deadline.label}</p>
                <p className="text-xs text-muted-foreground">
                  {formatDate(deadline.dueDate, i18n.language)}
                  {deadline.amount != null && ` · ${formatCHF(deadline.amount)}`}
                </p>
              </div>
              <button
                type="button"
                onClick={() => toggleDeadline.mutate(deadline)}
                disabled={toggleDeadline.isPending}
                className={cn(
                  'shrink-0 rounded-full px-2 py-0.5 text-xs font-medium transition-colors',
                  deadline.done
                    ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400'
                    : 'bg-amber-500/15 text-amber-600 dark:text-amber-400',
                )}
              >
                {deadline.done ? t('tax.done') : t('tax.status.open')}
              </button>
            </div>
          ))}
          <Button
            variant="outline"
            className="w-full sm:hidden"
            onClick={() => setDeadlineDialogOpen(true)}
          >
            <Plus className="mr-2 h-4 w-4" />
            {t('tax.addDeadline')}
          </Button>
        </CardContent>
      </Card>

      {/* Payments */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0">
          <CardTitle className="text-base">{t('tax.payments')}</CardTitle>
          <Button
            variant="outline"
            size="sm"
            className="hidden sm:inline-flex"
            onClick={() => setDialogOpen(true)}
          >
            <Plus className="mr-2 h-4 w-4" />
            {t('tax.addPayment')}
          </Button>
        </CardHeader>
        <CardContent className="space-y-3">
          {detail.payments.length === 0 && (
            <p className="text-sm text-muted-foreground">{t('tax.noPayments')}</p>
          )}
          {detail.payments.map((payment) => (
            <div key={payment.id} className="flex items-center gap-3 text-sm">
              <div className="min-w-0 flex-1">
                <p className="truncate">{payment.label}</p>
                <p className="text-xs text-muted-foreground">
                  {formatDate(payment.paymentDate, i18n.language)}
                </p>
              </div>
              <span className="font-medium">{formatCHF(payment.amount)}</span>
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 text-muted-foreground hover:text-destructive"
                aria-label={t('common.delete')}
                disabled={deletePayment.isPending}
                onClick={() => confirmDeletePayment(payment.id)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          ))}
          <Button
            variant="outline"
            className="w-full sm:hidden"
            onClick={() => setDialogOpen(true)}
          >
            <Plus className="mr-2 h-4 w-4" />
            {t('tax.addPayment')}
          </Button>
        </CardContent>
      </Card>

      {/* Add deadline dialog */}
      <Dialog open={deadlineDialogOpen} onOpenChange={setDeadlineDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t('tax.addDeadline')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="deadline-date">{t('tax.deadlineDate')}</Label>
              <Input
                id="deadline-date"
                type="date"
                value={deadlineDate}
                onChange={(e) => setDeadlineDate(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="deadline-label">{t('tax.deadlineLabel')}</Label>
              <Input
                id="deadline-label"
                value={deadlineLabel}
                onChange={(e) => setDeadlineLabel(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="deadline-amount">{t('tax.deadlineAmount')}</Label>
              <Input
                id="deadline-amount"
                type="number"
                value={deadlineAmount}
                onChange={(e) => setDeadlineAmount(e.target.value)}
              />
            </div>
            {addDeadline.error && (
              <p className="text-sm text-destructive">{addDeadline.error.message}</p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeadlineDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button
              onClick={() => addDeadline.mutate()}
              disabled={!deadlineDate || !deadlineLabel || addDeadline.isPending}
            >
              {addDeadline.isPending ? t('common.loading') : t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Add payment dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t('tax.addPayment')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="payment-date">{t('tax.paymentDate')}</Label>
              <Input
                id="payment-date"
                type="date"
                value={paymentDate}
                onChange={(e) => setPaymentDate(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="payment-amount">{t('tax.paymentAmount')} (CHF)</Label>
              <Input
                id="payment-amount"
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="payment-label">{t('tax.paymentLabel')}</Label>
              <Input
                id="payment-label"
                value={label}
                onChange={(e) => setLabel(e.target.value)}
              />
            </div>
            {addPayment.error && (
              <p className="text-sm text-destructive">{addPayment.error.message}</p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button
              onClick={() => addPayment.mutate()}
              disabled={!paymentDate || !amount || addPayment.isPending}
            >
              {addPayment.isPending ? t('common.loading') : t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
