import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { useAuth } from '@/auth/useAuth';
import { accountsApi } from '@/api/accounts';
import type { Account, AccountScope, AccountType, CreateAccountRequest } from '@/types';
import { ACCOUNT_SCOPES, scopeLabelKey } from './scopeLabel';

const ACCOUNT_TYPES: readonly AccountType[] = [
  'CHECKING', 'SAVINGS', 'CREDIT_CARD', 'INVESTMENT', 'VORSORGE', 'CASH', 'OTHER',
];

interface AccountDialogProps {
  /** `null` creates a new account, otherwise the account is edited. */
  account: Account | null;
  onClose: () => void;
}

/** Create/edit dialog for the account master data — mount fresh per open. */
export function AccountDialog({ account, onClose }: Readonly<AccountDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [name, setName] = useState(account?.name ?? '');
  const [type, setType] = useState<AccountType>(account?.type ?? 'CHECKING');
  const [currency, setCurrency] = useState(account?.currency ?? 'CHF');
  const [iban, setIban] = useState(account?.iban ?? '');
  const [bic, setBic] = useState(account?.bic ?? '');
  const [contractNumber, setContractNumber] = useState(account?.contractNumber ?? '');
  const [feeNote, setFeeNote] = useState(account?.feeNote ?? '');
  const [scope, setScope] = useState<AccountScope>(account?.scope ?? 'PRIVATE');
  const [toClose, setToClose] = useState(account?.toClose ?? false);

  const save = useMutation({
    mutationFn: (data: CreateAccountRequest) =>
      account ? accountsApi.update(token, account.id, data) : accountsApi.create(token, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // account names are denormalised into the cards list
      queryClient.invalidateQueries({ queryKey: ['cards'] });
      onClose();
    },
  });

  const handleSave = () => {
    save.mutate({
      name: name.trim(),
      type,
      currency: currency.trim().toUpperCase(),
      // balances are not managed on this page — keep the stored value, start new accounts at zero
      initialBalance: account?.initialBalance ?? '0',
      // the backend stores IBANs normalized (no spaces, uppercase)
      iban: iban.replaceAll(/\s+/g, '').toUpperCase() || undefined,
      bic: bic.trim() || undefined,
      contractNumber: contractNumber.trim() || undefined,
      feeNote: feeNote.trim() || undefined,
      scope,
      toClose,
    });
  };

  const submitLabel = account ? t('accounts.list.submitEdit') : t('accounts.list.submitCreate');

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {account ? t('accounts.list.editTitle') : t('accounts.list.createTitle')}
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="account-name">{t('accounts.list.name')}</Label>
            <Input id="account-name" value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="account-type">{t('accounts.list.type')}</Label>
              <Select value={type} onValueChange={(value) => setType(value as AccountType)}>
                <SelectTrigger id="account-type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ACCOUNT_TYPES.map((value) => (
                    <SelectItem key={value} value={value}>
                      {t(`accountType.${value}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="account-currency">{t('accounts.list.currency')}</Label>
              <Input
                id="account-currency"
                maxLength={3}
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="account-iban">{t('accounts.list.iban')}</Label>
            <Input
              id="account-iban"
              className="font-mono"
              value={iban}
              onChange={(e) => setIban(e.target.value)}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="account-bic">{t('accounts.list.bic')}</Label>
              <Input id="account-bic" value={bic} onChange={(e) => setBic(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="account-contract-number">{t('accounts.list.contractNumber')}</Label>
              <Input
                id="account-contract-number"
                value={contractNumber}
                onChange={(e) => setContractNumber(e.target.value)}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="account-fee-note">{t('accounts.list.fees')}</Label>
            <Input
              id="account-fee-note"
              value={feeNote}
              onChange={(e) => setFeeNote(e.target.value)}
            />
          </div>
          <div className="grid grid-cols-2 items-end gap-4">
            <div className="space-y-2">
              <Label htmlFor="account-scope">{t('accounts.list.scope')}</Label>
              <Select value={scope} onValueChange={(value) => setScope(value as AccountScope)}>
                <SelectTrigger id="account-scope">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ACCOUNT_SCOPES.map((value) => (
                    <SelectItem key={value} value={value}>
                      {t(scopeLabelKey(value))}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-center gap-2 pb-2">
              <Checkbox
                id="account-to-close"
                checked={toClose}
                onCheckedChange={(checked) => setToClose(checked === true)}
              />
              <Label htmlFor="account-to-close">{t('accounts.list.toClose')}</Label>
            </div>
          </div>
          {/* Server errors, e.g. the RFC-7807 detail on IBAN validation failures. */}
          {save.error && <p className="text-sm text-destructive">{save.error.message}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button
            onClick={handleSave}
            disabled={save.isPending || !name.trim() || currency.trim().length !== 3}
          >
            {save.isPending ? t('common.loading') : submitLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
