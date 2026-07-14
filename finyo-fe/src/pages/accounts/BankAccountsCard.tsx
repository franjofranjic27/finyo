import { Fragment, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Check, Copy, Pencil, Plus, Upload, X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/auth/useAuth';
import { accountsApi, formatIban } from '@/api/accounts';
import type { Account, CreateAccountRequest } from '@/types';
import { AccountDialog } from './AccountDialog';
import { parseAccountsCsv } from './accountsCsv';
import { ACCOUNT_SCOPES, scopeLabelKey } from './scopeLabel';

const COLUMN_COUNT = 8;

function IbanChip({ iban }: Readonly<{ iban: string | null }>) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) return undefined;
    const timer = setTimeout(() => setCopied(false), 2000);
    return () => clearTimeout(timer);
  }, [copied]);

  if (!iban) return <span className="text-muted-foreground">—</span>;

  const handleCopy = () => {
    // copy the raw normalized IBAN, not the display format with spaces
    void navigator.clipboard.writeText(iban).then(() => setCopied(true));
  };

  const label = copied ? t('accounts.list.ibanCopied') : t('accounts.list.copyIban');

  return (
    <span className="inline-flex items-center gap-2 whitespace-nowrap rounded-md bg-secondary px-2.5 py-1 font-mono text-xs">
      {formatIban(iban)}
      <button
        type="button"
        title={label}
        aria-label={label}
        className="text-muted-foreground transition-colors hover:text-foreground"
        onClick={handleCopy}
      >
        {copied ? (
          <Check className="h-3.5 w-3.5 text-emerald-500" aria-hidden="true" />
        ) : (
          <Copy className="h-3.5 w-3.5" aria-hidden="true" />
        )}
      </button>
    </span>
  );
}

function ScopeHeaderRow({ labelKey }: Readonly<{ labelKey: string }>) {
  const { t } = useTranslation();

  return (
    <tr className="border-b border-border">
      <td colSpan={COLUMN_COUNT} className="pb-1 pt-4">
        <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          {t(labelKey)}
        </span>
      </td>
    </tr>
  );
}

function AccountRow({ account, onEdit, onRemove, removing }: Readonly<{
  account: Account;
  onEdit: (account: Account) => void;
  onRemove: (account: Account) => void;
  removing: boolean;
}>) {
  const { t } = useTranslation();

  return (
    <tr className="border-b border-border last:border-0">
      <td className="py-2.5 pr-4">
        <p className="font-medium">{account.name}</p>
        <p className="text-xs text-muted-foreground">{t(`accountType.${account.type}`)}</p>
      </td>
      <td className="py-2.5 pr-4">
        <Badge variant="secondary">{account.currency}</Badge>
      </td>
      <td className="py-2.5 pr-4">
        <IbanChip iban={account.iban} />
      </td>
      <td className="py-2.5 pr-4">{account.bic ?? '—'}</td>
      <td className="py-2.5 pr-4">{account.contractNumber ?? '—'}</td>
      <td className="whitespace-nowrap py-2.5 pr-4 text-xs text-muted-foreground">
        {account.feeNote ?? '—'}
      </td>
      <td className="py-2.5 pr-4">
        {account.toClose && (
          <Badge variant="secondary" className="bg-amber-500/15 text-amber-600 hover:bg-amber-500/15">
            {t('accounts.list.toClose')}
          </Badge>
        )}
      </td>
      <td className="py-2.5 text-right">
        <span className="inline-flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground hover:text-foreground"
            aria-label={t('accounts.list.editTitle')}
            onClick={() => onEdit(account)}
          >
            <Pencil className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground hover:text-red-500"
            aria-label={t('accounts.list.deleteLabel')}
            disabled={removing}
            onClick={() => onRemove(account)}
          >
            <X className="h-3.5 w-3.5" />
          </Button>
        </span>
      </td>
    </tr>
  );
}

function CsvImportButton() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);

  const [summary, setSummary] = useState<string | null>(null);
  const [rowErrors, setRowErrors] = useState<string[]>([]);

  const importAccounts = useMutation({
    mutationFn: (items: CreateAccountRequest[]) => accountsApi.importAccounts(token, items),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // account names are denormalised into the cards list — an IBAN match may rename
      queryClient.invalidateQueries({ queryKey: ['cards'] });
      setSummary(t('accounts.list.csvResult', {
        created: result.created,
        updated: result.updated,
        failed: result.failed,
      }));
      setRowErrors(result.errors);
    },
  });

  const importFile = (file: File) => {
    const reader = new FileReader();
    reader.onload = () => {
      const items = parseAccountsCsv(String(reader.result ?? ''));
      setRowErrors([]);
      if (items.length === 0) {
        setSummary(t('accounts.list.csvNoRows'));
        return;
      }
      setSummary(null);
      importAccounts.mutate(items);
    };
    reader.readAsText(file);
  };

  return (
    <div className="flex flex-col items-end gap-1">
      <input
        ref={inputRef}
        type="file"
        accept=".csv,text/csv"
        className="sr-only"
        aria-label={t('accounts.list.csvImport')}
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) importFile(file);
          // Allow re-importing the same file after a fix.
          e.target.value = '';
        }}
      />
      <Button
        variant="ghost"
        size="sm"
        title={t('accounts.list.csvHint')}
        onClick={() => inputRef.current?.click()}
        disabled={importAccounts.isPending}
      >
        <Upload className="mr-1 h-4 w-4" />
        {importAccounts.isPending ? t('common.loading') : t('accounts.list.csvImport')}
      </Button>
      {summary && <p className="text-xs text-muted-foreground">{summary}</p>}
      {importAccounts.error && (
        <p className="text-xs text-destructive">{importAccounts.error.message}</p>
      )}
      {rowErrors.length > 0 && (
        <ul className="text-xs text-destructive">
          {rowErrors.map((error) => (
            <li key={error}>{error}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function BankAccountsCard({ accounts }: Readonly<{ accounts: Account[] }>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingAccount, setEditingAccount] = useState<Account | null>(null);

  const deleteAccount = useMutation({
    mutationFn: (id: string) => accountsApi.delete(token, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // cards reference accounts by name — a deleted account unlinks them
      queryClient.invalidateQueries({ queryKey: ['cards'] });
    },
  });

  const openCreate = () => {
    setEditingAccount(null);
    setDialogOpen(true);
  };

  const openEdit = (account: Account) => {
    setEditingAccount(account);
    setDialogOpen(true);
  };

  const confirmRemove = (account: Account) => {
    if (globalThis.confirm(t('accounts.list.confirmDelete', { name: account.name }))) {
      deleteAccount.mutate(account.id);
    }
  };

  const groups = ACCOUNT_SCOPES.map((scope) => ({
    scope,
    accounts: accounts.filter((account) => account.scope === scope),
  })).filter((group) => group.accounts.length > 0);

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between space-y-0">
        <div className="space-y-1">
          <CardTitle className="text-base">{t('accounts.list.title')}</CardTitle>
          <CardDescription className="text-xs">{t('accounts.list.subtitle')}</CardDescription>
        </div>
        <div className="flex items-start gap-1">
          <CsvImportButton />
          <Button variant="ghost" size="sm" onClick={openCreate}>
            <Plus className="h-4 w-4" />
            {t('accounts.list.add')}
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {accounts.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">
            {t('accounts.list.empty')}
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs text-muted-foreground">
                  <th className="py-2 pr-4 font-medium">{t('accounts.list.account')}</th>
                  <th className="py-2 pr-4 font-medium">{t('accounts.list.currency')}</th>
                  <th className="py-2 pr-4 font-medium">{t('accounts.list.iban')}</th>
                  <th className="py-2 pr-4 font-medium">{t('accounts.list.bic')}</th>
                  <th className="py-2 pr-4 font-medium">{t('accounts.list.contractNumber')}</th>
                  <th className="py-2 pr-4 font-medium">{t('accounts.list.fees')}</th>
                  <th className="py-2 pr-4 font-medium">{t('accounts.list.status')}</th>
                  <th className="py-2" aria-hidden="true" />
                </tr>
              </thead>
              <tbody>
                {groups.map((group) => (
                  <Fragment key={group.scope}>
                    <ScopeHeaderRow labelKey={scopeLabelKey(group.scope)} />
                    {group.accounts.map((account) => (
                      <AccountRow
                        key={account.id}
                        account={account}
                        onEdit={openEdit}
                        onRemove={confirmRemove}
                        removing={deleteAccount.isPending}
                      />
                    ))}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
      {dialogOpen && (
        <AccountDialog account={editingAccount} onClose={() => setDialogOpen(false)} />
      )}
    </Card>
  );
}
