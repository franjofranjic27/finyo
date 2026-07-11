import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
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
import type { Account, AccountScope, PaymentCard, PaymentCardInput } from '@/types';
import { ACCOUNT_SCOPES, scopeLabelKey } from './scopeLabel';

/** Radix Select items must not have an empty value — sentinel for "no linked account". */
const NO_ACCOUNT = 'NONE';

interface CardDialogProps {
  /** `null` creates a new card, otherwise the card is edited. */
  card: PaymentCard | null;
  /** Loaded accounts for the "linked account" select. */
  accounts: Account[];
  onClose: () => void;
}

/** Create/edit dialog for a payment card — mount fresh per open. */
export function CardDialog({ card, accounts, onClose }: Readonly<CardDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [name, setName] = useState(card?.name ?? '');
  const [provider, setProvider] = useState(card?.provider ?? '');
  const [accountId, setAccountId] = useState(card?.accountId ?? NO_ACCOUNT);
  const [currency, setCurrency] = useState(card?.currency ?? 'CHF');
  const [feeNote, setFeeNote] = useState(card?.feeNote ?? '');
  const [scope, setScope] = useState<AccountScope>(card?.scope ?? 'PRIVATE');

  const save = useMutation({
    mutationFn: (data: PaymentCardInput) =>
      card ? accountsApi.updateCard(token, card.id, data) : accountsApi.createCard(token, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cards'] });
      onClose();
    },
  });

  const handleSave = () => {
    save.mutate({
      name: name.trim(),
      provider: provider.trim() || undefined,
      accountId: accountId === NO_ACCOUNT ? undefined : accountId,
      currency: currency.trim().toUpperCase() || undefined,
      feeNote: feeNote.trim() || undefined,
      scope,
    });
  };

  const submitLabel = card ? t('accounts.cards.submitEdit') : t('accounts.cards.submitCreate');

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {card ? t('accounts.cards.editTitle') : t('accounts.cards.createTitle')}
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="card-name">{t('accounts.cards.name')}</Label>
            <Input id="card-name" value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="card-provider">{t('accounts.cards.provider')}</Label>
            <Input
              id="card-provider"
              value={provider}
              onChange={(e) => setProvider(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="card-account">{t('accounts.cards.account')}</Label>
            <Select value={accountId} onValueChange={setAccountId}>
              <SelectTrigger id="card-account">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NO_ACCOUNT}>—</SelectItem>
                {accounts.map((account) => (
                  <SelectItem key={account.id} value={account.id}>
                    {account.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="card-currency">{t('accounts.cards.currency')}</Label>
              <Input
                id="card-currency"
                maxLength={3}
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="card-scope">{t('accounts.cards.scope')}</Label>
              <Select value={scope} onValueChange={(value) => setScope(value as AccountScope)}>
                <SelectTrigger id="card-scope">
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
          </div>
          <div className="space-y-2">
            <Label htmlFor="card-fee-note">{t('accounts.cards.fees')}</Label>
            <Input id="card-fee-note" value={feeNote} onChange={(e) => setFeeNote(e.target.value)} />
          </div>
          {save.error && <p className="text-sm text-destructive">{save.error.message}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleSave} disabled={save.isPending || !name.trim() || ![0, 3].includes(currency.trim().length)}>
            {save.isPending ? t('common.loading') : submitLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
