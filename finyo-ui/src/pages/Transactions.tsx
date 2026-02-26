import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Upload, Trash2, ChevronLeft, ChevronRight } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
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
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { transactionsApi } from '@/api/transactions';
import { accountsApi } from '@/api/accounts';
import { apiRequest } from '@/api/client';
import { formatCHF, formatDate, amountColour } from '@/lib/formatters';
import type { Transaction, Category, Account } from '@/types';

const IMPORT_PRESETS = ['CUSTOM', 'UBS', 'RAIFFEISEN', 'POST_FINANCE'];

interface ImportResult { imported: number; skipped: number; errors: string[] }

// --- Add Transaction Dialog ---

function AddTransactionDialog({
  open,
  onClose,
  accounts,
  categories,
  token,
}: {
  open: boolean;
  onClose: () => void;
  accounts: Account[];
  categories: Category[];
  token: string;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [description, setDescription] = useState('');
  const [accountId, setAccountId] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [currency, setCurrency] = useState('CHF');

  const { mutate, isPending } = useMutation({
    mutationFn: () =>
      transactionsApi.create(token, {
        amount,
        currency,
        date,
        description: description || undefined,
        accountId,
        categoryId: categoryId || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      onClose();
      setAmount('');
      setDate(new Date().toISOString().slice(0, 10));
      setDescription('');
      setAccountId('');
      setCategoryId('');
    },
  });

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('transactions.addTransaction')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>{t('transactions.amount')} (negative = expense)</Label>
              <Input
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="-89.50"
                step={0.01}
              />
            </div>
            <div className="space-y-2">
              <Label>{t('common.currency')}</Label>
              <Input value={currency} onChange={(e) => setCurrency(e.target.value)} placeholder="CHF" maxLength={3} />
            </div>
          </div>
          <div className="space-y-2">
            <Label>{t('transactions.date')}</Label>
            <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>{t('transactions.description')}</Label>
            <Input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Migros Zürich" />
          </div>
          <div className="space-y-2">
            <Label>{t('transactions.account')} *</Label>
            <Select value={accountId} onValueChange={setAccountId}>
              <SelectTrigger><SelectValue placeholder="Select account..." /></SelectTrigger>
              <SelectContent>
                {accounts.map((a) => <SelectItem key={a.id} value={a.id}>{a.name}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>{t('transactions.category')} (optional)</Label>
            <Select value={categoryId} onValueChange={setCategoryId}>
              <SelectTrigger><SelectValue placeholder="Select category..." /></SelectTrigger>
              <SelectContent>
                {categories.map((c) => (
                  <SelectItem key={c.id} value={c.id}>
                    {c.icon ? `${c.icon} ` : ''}{c.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={() => mutate()} disabled={isPending || !amount || !accountId}>
            {isPending ? t('common.loading') : t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// --- CSV Import Dialog ---

function CsvImportDialog({
  open,
  onClose,
  accounts,
  token,
}: {
  open: boolean;
  onClose: () => void;
  accounts: Account[];
  token: string;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [accountId, setAccountId] = useState('');
  const [preset, setPreset] = useState('CUSTOM');
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);

  const { mutate, isPending } = useMutation({
    mutationFn: async () => {
      if (!file || !accountId) throw new Error('Missing file or account');
      const isExcel = file.name.endsWith('.xlsx') || file.name.endsWith('.xls');
      const endpoint = isExcel
        ? '/api/v1/transactions/import/excel'
        : '/api/v1/transactions/import/csv';
      const form = new FormData();
      form.append('file', file);
      form.append('accountId', accountId);
      form.append('preset', preset);
      form.append('hasHeader', 'true');
      form.append('skipDuplicates', 'true');
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: form,
      });
      if (!res.ok) throw new Error(await res.text());
      return res.json() as Promise<ImportResult>;
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      setResult(data);
    },
  });

  const handleClose = () => {
    onClose();
    setResult(null);
    setFile(null);
    setAccountId('');
    setPreset('CUSTOM');
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('transactions.importCsv')}</DialogTitle>
        </DialogHeader>
        {result ? (
          <div className="py-4 space-y-2">
            <p className="text-sm font-medium text-emerald-500">Import complete</p>
            <p className="text-sm">Imported: <strong>{result.imported}</strong></p>
            <p className="text-sm">Skipped duplicates: <strong>{result.skipped}</strong></p>
            {result.errors.length > 0 && (
              <div className="mt-2">
                <p className="text-sm font-medium text-red-500">Errors:</p>
                <ul className="text-xs text-red-400 list-disc list-inside">
                  {result.errors.slice(0, 5).map((e, i) => <li key={i}>{e}</li>)}
                </ul>
              </div>
            )}
          </div>
        ) : (
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>{t('transactions.account')} *</Label>
              <Select value={accountId} onValueChange={setAccountId}>
                <SelectTrigger><SelectValue placeholder="Select account..." /></SelectTrigger>
                <SelectContent>
                  {accounts.map((a) => <SelectItem key={a.id} value={a.id}>{a.name}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>Bank Preset</Label>
              <Select value={preset} onValueChange={setPreset}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {IMPORT_PRESETS.map((p) => <SelectItem key={p} value={p}>{p}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>File (.csv or .xlsx)</Label>
              <Input
                type="file"
                accept=".csv,.xlsx,.xls"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              />
            </div>
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={handleClose}>{result ? t('common.close') : t('common.cancel')}</Button>
          {!result && (
            <Button onClick={() => mutate()} disabled={isPending || !file || !accountId}>
              {isPending ? t('common.loading') : 'Import'}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// --- Transaction row ---

function TxRow({ tx, lang, onDelete }: { tx: Transaction; lang: string; onDelete: (id: string) => void }) {
  const { t } = useTranslation();
  const amount = parseFloat(tx.amount);
  return (
    <div className="flex items-center py-2.5 gap-3 border-b last:border-0">
      <span className="w-24 shrink-0 text-xs text-muted-foreground">{formatDate(tx.date, lang)}</span>
      <span className="flex-1 min-w-0 text-sm truncate">{tx.description ?? '—'}</span>
      <span className="w-28 shrink-0 hidden md:block">
        {tx.categoryName && (
          <Badge variant="secondary" className="text-xs truncate max-w-full">{tx.categoryName}</Badge>
        )}
      </span>
      <span className="w-28 shrink-0 hidden lg:block text-xs text-muted-foreground truncate">{tx.accountName}</span>
      <span className={`w-24 shrink-0 text-sm font-semibold text-right ${amountColour(amount)}`}>
        {formatCHF(amount)}
      </span>
      <Button
        variant="ghost"
        size="icon"
        className="h-7 w-7 shrink-0 text-muted-foreground hover:text-red-500"
        onClick={() => {
          if (window.confirm(t('transactions.confirmDelete'))) onDelete(tx.id);
        }}
      >
        <Trash2 className="h-3.5 w-3.5" />
      </Button>
    </div>
  );
}

// --- Main page ---

export function Transactions() {
  const { t, i18n } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [page, setPage] = useState(0);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [accountFilter, setAccountFilter] = useState('');
  const [addOpen, setAddOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);

  const { data: txPage, isLoading } = useQuery({
    queryKey: ['transactions', 'page', page, from, to, accountFilter],
    queryFn: () =>
      transactionsApi.getPage(token, {
        page,
        size: 30,
        from: from || undefined,
        to: to || undefined,
        accountId: accountFilter || undefined,
      }),
    enabled: !!token,
    placeholderData: (prev) => prev,
  });

  const { data: accounts = [] } = useQuery({
    queryKey: ['accounts'],
    queryFn: () => accountsApi.getAll(token),
    enabled: !!token,
  });

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: () => apiRequest<Category[]>('/categories', {}, token),
    enabled: !!token,
  });

  const { mutate: deleteTx } = useMutation({
    mutationFn: (id: string) => transactionsApi.delete(token, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['transactions'] }),
  });

  const transactions = txPage?.content ?? [];
  const totalPages = txPage?.totalPages ?? 0;
  const totalElements = txPage?.totalElements ?? 0;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <h1 className="text-2xl font-semibold">{t('transactions.title')}</h1>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => setImportOpen(true)}>
            <Upload className="h-4 w-4 mr-1" /> {t('transactions.importCsv')}
          </Button>
          <Button size="sm" onClick={() => setAddOpen(true)}>
            <Plus className="h-4 w-4 mr-1" /> {t('transactions.addTransaction')}
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-3 flex-wrap items-end">
        <div className="space-y-1">
          <Label className="text-xs">From</Label>
          <Input
            type="date"
            value={from}
            onChange={(e) => { setFrom(e.target.value); setPage(0); }}
            className="h-8 text-sm w-36"
          />
        </div>
        <div className="space-y-1">
          <Label className="text-xs">To</Label>
          <Input
            type="date"
            value={to}
            onChange={(e) => { setTo(e.target.value); setPage(0); }}
            className="h-8 text-sm w-36"
          />
        </div>
        <div className="space-y-1">
          <Label className="text-xs">{t('transactions.account')}</Label>
          <Select value={accountFilter} onValueChange={(v) => { setAccountFilter(v === '_all' ? '' : v); setPage(0); }}>
            <SelectTrigger className="h-8 text-sm w-44">
              <SelectValue placeholder="All accounts" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="_all">All accounts</SelectItem>
              {accounts.map((a) => <SelectItem key={a.id} value={a.id}>{a.name}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        {(from || to || accountFilter) && (
          <Button
            variant="ghost"
            size="sm"
            className="h-8 text-xs"
            onClick={() => { setFrom(''); setTo(''); setAccountFilter(''); setPage(0); }}
          >
            Clear filters
          </Button>
        )}
      </div>

      {/* Table */}
      <Card>
        <CardHeader className="py-3">
          <CardTitle className="text-sm font-medium text-muted-foreground">
            {isLoading ? t('common.loading') : `${totalElements} transactions`}
          </CardTitle>
        </CardHeader>
        <CardContent className="px-4 pb-2">
          {/* Column headers */}
          <div className="flex items-center gap-3 pb-2 border-b text-xs text-muted-foreground font-medium">
            <span className="w-24 shrink-0">{t('transactions.date')}</span>
            <span className="flex-1">{t('transactions.description')}</span>
            <span className="w-28 shrink-0 hidden md:block">{t('transactions.category')}</span>
            <span className="w-28 shrink-0 hidden lg:block">{t('transactions.account')}</span>
            <span className="w-24 shrink-0 text-right">{t('transactions.amount')}</span>
            <span className="w-7 shrink-0" />
          </div>

          {isLoading ? (
            Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="flex items-center py-2.5 gap-3 border-b last:border-0">
                <Skeleton className="h-4 w-20" />
                <Skeleton className="h-4 flex-1" />
                <Skeleton className="h-4 w-20 hidden md:block" />
                <Skeleton className="h-4 w-20 hidden lg:block" />
                <Skeleton className="h-4 w-20" />
              </div>
            ))
          ) : transactions.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">{t('transactions.noTransactions')}</p>
          ) : (
            transactions.map((tx) => (
              <TxRow
                key={tx.id}
                tx={tx}
                lang={i18n.language}
                onDelete={(id) => deleteTx(id)}
              />
            ))
          )}
        </CardContent>
      </Card>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <span className="text-muted-foreground">
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      <AddTransactionDialog
        open={addOpen}
        onClose={() => setAddOpen(false)}
        accounts={accounts}
        categories={categories}
        token={token}
      />
      <CsvImportDialog
        open={importOpen}
        onClose={() => setImportOpen(false)}
        accounts={accounts}
        token={token}
      />
    </div>
  );
}
