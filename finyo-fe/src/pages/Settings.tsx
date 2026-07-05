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
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { useAuth } from '@/auth/useAuth';
import { accountsApi } from '@/api/accounts';
import { apiRequest } from '@/api/client';
import { formatCHF } from '@/lib/formatters';
import type { Account, Category, CreateAccountRequest } from '@/types';

const ACCOUNT_TYPES: Account['type'][] = [
  'CHECKING', 'SAVINGS', 'CREDIT_CARD', 'INVESTMENT', 'CASH', 'OTHER',
];

// --- Accounts Tab ---

function AddAccountDialog({
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
  const [type, setType] = useState<Account['type']>('CHECKING');
  const [currency, setCurrency] = useState('CHF');
  const [initialBalance, setInitialBalance] = useState('0');
  const [color, setColor] = useState('');

  const { mutate, isPending } = useMutation({
    mutationFn: () =>
      accountsApi.create(token, {
        name,
        type,
        currency,
        initialBalance,
        color: color || undefined,
      } as CreateAccountRequest),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      onClose();
      setName('');
      setType('CHECKING');
      setCurrency('CHF');
      setInitialBalance('0');
      setColor('');
    },
  });

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add Account</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label>Name *</Label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="UBS Checking" />
          </div>
          <div className="space-y-2">
            <Label>Type</Label>
            <Select value={type} onValueChange={(v) => setType(v as Account['type'])}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {ACCOUNT_TYPES.map((t) => <SelectItem key={t} value={t}>{t.replace('_', ' ')}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>{t('common.currency')}</Label>
              <Input value={currency} onChange={(e) => setCurrency(e.target.value)} placeholder="CHF" maxLength={3} />
            </div>
            <div className="space-y-2">
              <Label>Initial Balance (CHF)</Label>
              <Input type="number" value={initialBalance} onChange={(e) => setInitialBalance(e.target.value)} step={0.01} />
            </div>
          </div>
          <div className="space-y-2">
            <Label>Color (hex, optional)</Label>
            <Input value={color} onChange={(e) => setColor(e.target.value)} placeholder="#6366f1" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={() => mutate()} disabled={isPending || !name}>
            {isPending ? t('common.loading') : t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function AccountsTab({ token }: { token: string }) {
  const queryClient = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);

  const { data: accounts, isLoading } = useQuery({
    queryKey: ['accounts'],
    queryFn: () => accountsApi.getAll(token),
    enabled: !!token,
  });

  const { mutate: deleteAccount } = useMutation({
    mutationFn: (id: string) => accountsApi.delete(token, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['accounts'] }),
  });

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button size="sm" onClick={() => setAddOpen(true)}>
          <Plus className="h-4 w-4 mr-1" /> Add Account
        </Button>
      </div>

      {isLoading ? (
        Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-16 w-full" />)
      ) : (accounts ?? []).length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-foreground">No accounts yet.</p>
      ) : (
        <div className="space-y-2">
          {(accounts ?? []).map((account) => (
            <Card key={account.id}>
              <CardContent className="py-3 px-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    {account.color && (
                      <span
                        className="h-4 w-4 rounded-full shrink-0"
                        style={{ background: account.color }}
                      />
                    )}
                    <div>
                      <p className="font-medium text-sm">{account.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {formatCHF(account.initialBalance)} initial · {account.currency}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant="secondary" className="text-xs">
                      {account.type.replace('_', ' ')}
                    </Badge>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 text-muted-foreground hover:text-red-500"
                      onClick={() => {
                        if (window.confirm(`Delete account "${account.name}"? This cannot be undone.`)) {
                          deleteAccount(account.id);
                        }
                      }}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <AddAccountDialog open={addOpen} onClose={() => setAddOpen(false)} token={token} />
    </div>
  );
}

// --- Categories Tab ---

function AddCategoryDialog({
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
  const [name, setName] = useState('');
  const [type, setType] = useState<'EXPENSE' | 'INCOME'>('EXPENSE');
  const [icon, setIcon] = useState('');
  const [color, setColor] = useState('');
  const [parentId, setParentId] = useState('');

  const { mutate, isPending } = useMutation({
    mutationFn: () =>
      apiRequest<Category>('/categories', {
        method: 'POST',
        body: JSON.stringify({
          name,
          type,
          icon: icon || undefined,
          color: color || undefined,
          parentId: parentId || undefined,
        }),
      }, token),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      onClose();
      setName('');
      setType('EXPENSE');
      setIcon('');
      setColor('');
      setParentId('');
    },
  });

  const topLevelCats = categories.filter((c) => !c.parentId && c.type === type);

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add Category</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label>Name *</Label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Groceries" />
          </div>
          <div className="space-y-2">
            <Label>Type</Label>
            <Select value={type} onValueChange={(v) => { setType(v as 'EXPENSE' | 'INCOME'); setParentId(''); }}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="EXPENSE">Expense</SelectItem>
                <SelectItem value="INCOME">Income</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {topLevelCats.length > 0 && (
            <div className="space-y-2">
              <Label>Parent Category (optional)</Label>
              <Select value={parentId} onValueChange={(v) => setParentId(v === '_none' ? '' : v)}>
                <SelectTrigger><SelectValue placeholder="No parent (top-level)" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="_none">No parent (top-level)</SelectItem>
                  {topLevelCats.map((c) => (
                    <SelectItem key={c.id} value={c.id}>
                      {c.icon ? `${c.icon} ` : ''}{c.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>Icon (emoji)</Label>
              <Input value={icon} onChange={(e) => setIcon(e.target.value)} placeholder="🛒" />
            </div>
            <div className="space-y-2">
              <Label>Color (hex)</Label>
              <Input value={color} onChange={(e) => setColor(e.target.value)} placeholder="#6366f1" />
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={() => mutate()} disabled={isPending || !name}>
            {isPending ? t('common.loading') : t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function CategoriesTab({ token }: { token: string }) {
  const queryClient = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);

  const { data: categories, isLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: () => apiRequest<Category[]>('/categories', {}, token),
    enabled: !!token,
  });

  const { mutate: deleteCategory } = useMutation({
    mutationFn: (id: string) =>
      apiRequest<void>(`/categories/${id}`, { method: 'DELETE' }, token),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['categories'] }),
  });

  const expenses = (categories ?? []).filter((c) => c.type === 'EXPENSE');
  const incomes = (categories ?? []).filter((c) => c.type === 'INCOME');

  const CategoryRow = ({ cat }: { cat: Category }) => (
    <div className="flex items-center justify-between py-2">
      <div className="flex items-center gap-2">
        {cat.color && (
          <span className="h-3 w-3 rounded-full shrink-0" style={{ background: cat.color }} />
        )}
        {cat.icon && <span className="text-base">{cat.icon}</span>}
        <span className="text-sm">{cat.parentName ? `${cat.parentName} › ` : ''}{cat.name}</span>
      </div>
      <Button
        variant="ghost"
        size="icon"
        className="h-7 w-7 text-muted-foreground hover:text-red-500"
        onClick={() => {
          if (window.confirm(`Delete category "${cat.name}"?`)) deleteCategory(cat.id);
        }}
      >
        <Trash2 className="h-3.5 w-3.5" />
      </Button>
    </div>
  );

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button size="sm" onClick={() => setAddOpen(true)}>
          <Plus className="h-4 w-4 mr-1" /> Add Category
        </Button>
      </div>

      {isLoading ? (
        Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)
      ) : (
        <div className="space-y-4">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <h3 className="text-sm font-medium">Expenses</h3>
              <Badge variant="secondary">{expenses.length}</Badge>
            </div>
            <Card>
              <CardContent className="px-4 py-1 divide-y">
                {expenses.length === 0 ? (
                  <p className="py-3 text-sm text-muted-foreground text-center">No expense categories</p>
                ) : (
                  expenses.map((c) => <CategoryRow key={c.id} cat={c} />)
                )}
              </CardContent>
            </Card>
          </div>

          <Separator />

          <div>
            <div className="flex items-center gap-2 mb-2">
              <h3 className="text-sm font-medium">Income</h3>
              <Badge variant="secondary">{incomes.length}</Badge>
            </div>
            <Card>
              <CardContent className="px-4 py-1 divide-y">
                {incomes.length === 0 ? (
                  <p className="py-3 text-sm text-muted-foreground text-center">No income categories</p>
                ) : (
                  incomes.map((c) => <CategoryRow key={c.id} cat={c} />)
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      )}

      <AddCategoryDialog
        open={addOpen}
        onClose={() => setAddOpen(false)}
        categories={categories ?? []}
        token={token}
      />
    </div>
  );
}

// --- Main page ---

export function Settings() {
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Settings</h1>
      <Tabs defaultValue="accounts">
        <TabsList>
          <TabsTrigger value="accounts">Accounts</TabsTrigger>
          <TabsTrigger value="categories">Categories</TabsTrigger>
        </TabsList>
        <TabsContent value="accounts" className="mt-4">
          <AccountsTab token={token} />
        </TabsContent>
        <TabsContent value="categories" className="mt-4">
          <CategoriesTab token={token} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
