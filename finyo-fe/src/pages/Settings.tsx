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
import { ProfileTab } from './settings/ProfileTab';
import type { Account, Category, CreateAccountRequest } from '@/types';

const ACCOUNT_TYPES: Account['type'][] = [
  'CHECKING', 'SAVINGS', 'CREDIT_CARD', 'INVESTMENT', 'CASH', 'OTHER',
];

const ACCOUNT_SKELETON_KEYS = ['sk-1', 'sk-2', 'sk-3'];
const CATEGORY_SKELETON_KEYS = ['sk-1', 'sk-2', 'sk-3', 'sk-4', 'sk-5', 'sk-6'];

// --- Accounts Tab ---

function AddAccountDialog({
  open,
  onClose,
  token,
}: Readonly<{
  open: boolean;
  onClose: () => void;
  token: string;
}>) {
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
          <DialogTitle>{t('settings.accounts.dialog.title')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label>{t('settings.accounts.dialog.name')}</Label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t('settings.accounts.dialog.namePlaceholder')}
            />
          </div>
          <div className="space-y-2">
            <Label>{t('settings.accounts.dialog.type')}</Label>
            <Select value={type} onValueChange={(v) => setType(v as Account['type'])}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {ACCOUNT_TYPES.map((accountType) => (
                  <SelectItem key={accountType} value={accountType}>
                    {t(`accountType.${accountType}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label>{t('common.currency')}</Label>
              <Input value={currency} onChange={(e) => setCurrency(e.target.value)} placeholder="CHF" maxLength={3} />
            </div>
            <div className="space-y-2">
              <Label>{t('settings.accounts.dialog.initialBalance')}</Label>
              <Input type="number" value={initialBalance} onChange={(e) => setInitialBalance(e.target.value)} step={0.01} />
            </div>
          </div>
          <div className="space-y-2">
            <Label>{t('settings.accounts.dialog.color')}</Label>
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

function AccountsTab({ token }: Readonly<{ token: string }>) {
  const { t } = useTranslation();
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

  const accountList = accounts ?? [];

  let content: React.ReactNode;
  if (isLoading) {
    content = ACCOUNT_SKELETON_KEYS.map((key) => <Skeleton key={key} className="h-16 w-full" />);
  } else if (accountList.length === 0) {
    content = (
      <p className="py-8 text-center text-sm text-muted-foreground">
        {t('settings.accounts.empty')}
      </p>
    );
  } else {
    content = (
      <div className="space-y-2">
        {accountList.map((account) => (
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
                      {t('settings.accounts.initial', {
                        amount: formatCHF(account.initialBalance),
                        currency: account.currency,
                      })}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant="secondary" className="text-xs">
                    {t(`accountType.${account.type}`)}
                  </Badge>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-7 w-7 text-muted-foreground hover:text-red-500"
                    onClick={() => {
                      if (globalThis.confirm(t('settings.accounts.confirmDelete', { name: account.name }))) {
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
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button size="sm" onClick={() => setAddOpen(true)}>
          <Plus className="h-4 w-4 mr-1" /> {t('settings.accounts.add')}
        </Button>
      </div>

      {content}

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
}: Readonly<{
  open: boolean;
  onClose: () => void;
  categories: Category[];
  token: string;
}>) {
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
          <DialogTitle>{t('settings.categories.dialog.title')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label>{t('settings.categories.dialog.name')}</Label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t('settings.categories.dialog.namePlaceholder')}
            />
          </div>
          <div className="space-y-2">
            <Label>{t('settings.categories.dialog.type')}</Label>
            <Select value={type} onValueChange={(v) => { setType(v as 'EXPENSE' | 'INCOME'); setParentId(''); }}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="EXPENSE">{t('settings.categories.dialog.expense')}</SelectItem>
                <SelectItem value="INCOME">{t('settings.categories.dialog.income')}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {topLevelCats.length > 0 && (
            <div className="space-y-2">
              <Label>{t('settings.categories.dialog.parent')}</Label>
              <Select value={parentId} onValueChange={(v) => setParentId(v === '_none' ? '' : v)}>
                <SelectTrigger>
                  <SelectValue placeholder={t('settings.categories.dialog.noParent')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="_none">{t('settings.categories.dialog.noParent')}</SelectItem>
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
              <Label>{t('settings.categories.dialog.icon')}</Label>
              <Input value={icon} onChange={(e) => setIcon(e.target.value)} placeholder="🛒" />
            </div>
            <div className="space-y-2">
              <Label>{t('settings.categories.dialog.color')}</Label>
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

function CategoryRow({ cat, onDelete }: Readonly<{ cat: Category; onDelete: (id: string) => void }>) {
  const { t } = useTranslation();

  return (
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
          if (globalThis.confirm(t('settings.categories.confirmDelete', { name: cat.name }))) {
            onDelete(cat.id);
          }
        }}
      >
        <Trash2 className="h-3.5 w-3.5" />
      </Button>
    </div>
  );
}

function CategoriesTab({ token }: Readonly<{ token: string }>) {
  const { t } = useTranslation();
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

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button size="sm" onClick={() => setAddOpen(true)}>
          <Plus className="h-4 w-4 mr-1" /> {t('settings.categories.add')}
        </Button>
      </div>

      {isLoading ? (
        CATEGORY_SKELETON_KEYS.map((key) => <Skeleton key={key} className="h-10 w-full" />)
      ) : (
        <div className="space-y-4">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <h3 className="text-sm font-medium">{t('settings.categories.expenses')}</h3>
              <Badge variant="secondary">{expenses.length}</Badge>
            </div>
            <Card>
              <CardContent className="px-4 py-1 divide-y">
                {expenses.length === 0 ? (
                  <p className="py-3 text-sm text-muted-foreground text-center">
                    {t('settings.categories.emptyExpenses')}
                  </p>
                ) : (
                  expenses.map((c) => <CategoryRow key={c.id} cat={c} onDelete={deleteCategory} />)
                )}
              </CardContent>
            </Card>
          </div>

          <Separator />

          <div>
            <div className="flex items-center gap-2 mb-2">
              <h3 className="text-sm font-medium">{t('settings.categories.income')}</h3>
              <Badge variant="secondary">{incomes.length}</Badge>
            </div>
            <Card>
              <CardContent className="px-4 py-1 divide-y">
                {incomes.length === 0 ? (
                  <p className="py-3 text-sm text-muted-foreground text-center">
                    {t('settings.categories.emptyIncome')}
                  </p>
                ) : (
                  incomes.map((c) => <CategoryRow key={c.id} cat={c} onDelete={deleteCategory} />)
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
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">{t('settings.title')}</h1>
      <Tabs defaultValue="profile">
        <TabsList>
          <TabsTrigger value="profile">{t('settings.tabs.profile')}</TabsTrigger>
          <TabsTrigger value="accounts">{t('settings.tabs.accounts')}</TabsTrigger>
          <TabsTrigger value="categories">{t('settings.tabs.categories')}</TabsTrigger>
        </TabsList>
        <TabsContent value="profile" className="mt-4">
          <ProfileTab />
        </TabsContent>
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
