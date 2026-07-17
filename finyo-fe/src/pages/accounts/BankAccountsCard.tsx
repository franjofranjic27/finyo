import { Fragment, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Check, Copy, Pencil, Plus, X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { CsvImportButton } from '@/components/CsvImportButton';
import { useAuth } from '@/auth/useAuth';
import { accountsApi, formatIban } from '@/api/accounts';
import type { Account } from '@/types';
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

/** Mobile list row: name + IBAN chip, secondary details below, actions on the right. */
function AccountListItem({ account, onEdit, onRemove, removing }: Readonly<{
  account: Account;
  onEdit: (account: Account) => void;
  onRemove: (account: Account) => void;
  removing: boolean;
}>) {
  const { t } = useTranslation();

  const details = [t(`accountType.${account.type}`), account.bic, account.contractNumber, account.feeNote]
    .filter(Boolean)
    .join(' · ');

  return (
    <li className="flex items-start gap-3 border-b border-border py-3 last:border-0">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2 font-medium">
          {account.name}
          {account.toClose && (
            <Badge variant="secondary" className="bg-amber-500/15 text-amber-600 hover:bg-amber-500/15">
              {t('accounts.list.toClose')}
            </Badge>
          )}
        </div>
        {account.iban && (
          <p className="mt-1">
            <IbanChip iban={account.iban} />
          </p>
        )}
        <p className="mt-1 text-xs text-muted-foreground">{details}</p>
      </div>
      <div className="flex shrink-0 items-center gap-1">
        <Badge variant="secondary">{account.currency}</Badge>
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8 text-muted-foreground hover:text-foreground"
          aria-label={t('accounts.list.editTitle')}
          onClick={() => onEdit(account)}
        >
          <Pencil className="h-3.5 w-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8 text-muted-foreground hover:text-red-500"
          aria-label={t('accounts.list.deleteLabel')}
          disabled={removing}
          onClick={() => onRemove(account)}
        >
          <X className="h-3.5 w-3.5" />
        </Button>
      </div>
    </li>
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
      <CardHeader className="flex flex-col gap-3 space-y-0 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <CardTitle className="text-base">{t('accounts.list.title')}</CardTitle>
          <CardDescription className="text-xs">{t('accounts.list.subtitle')}</CardDescription>
        </div>
        <div className="flex items-start gap-1">
          <CsvImportButton
            i18nPrefix="accounts.list"
            parse={parseAccountsCsv}
            importItems={accountsApi.importAccounts}
            // account names are denormalised into the cards list — an IBAN match may rename
            invalidateKeys={[['accounts'], ['cards']]}
          />
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
          <>
            {/* Mobile: grouped list rows — the table does not fit on 390 px. */}
            <div className="md:hidden">
              {groups.map((group) => (
                <div key={group.scope}>
                  <p className="border-b border-border pb-1.5 pt-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground first:pt-0">
                    {t(scopeLabelKey(group.scope))}
                  </p>
                  <ul>
                    {group.accounts.map((account) => (
                      <AccountListItem
                        key={account.id}
                        account={account}
                        onEdit={openEdit}
                        onRemove={confirmRemove}
                        removing={deleteAccount.isPending}
                      />
                    ))}
                  </ul>
                </div>
              ))}
            </div>

            <div className="hidden overflow-x-auto md:block">
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
          </>
        )}
      </CardContent>
      {dialogOpen && (
        <AccountDialog account={editingAccount} onClose={() => setDialogOpen(false)} />
      )}
    </Card>
  );
}
