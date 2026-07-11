import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/auth/useAuth';
import { accountsApi } from '@/api/accounts';
import type { Account, PaymentCard } from '@/types';
import { CardDialog } from './CardDialog';
import { scopeLabelKey } from './scopeLabel';

function CardRow({ card, onEdit, onRemove, removing }: Readonly<{
  card: PaymentCard;
  onEdit: (card: PaymentCard) => void;
  onRemove: (card: PaymentCard) => void;
  removing: boolean;
}>) {
  const { t } = useTranslation();

  return (
    <tr className="border-b border-border last:border-0">
      <td className="py-2.5 pr-4">
        <p className="font-medium">{card.name}</p>
        <p className="text-xs text-muted-foreground">{t(scopeLabelKey(card.scope))}</p>
      </td>
      <td className="py-2.5 pr-4">{card.provider ?? '—'}</td>
      <td className="py-2.5 pr-4">{card.accountName ?? '—'}</td>
      <td className="py-2.5 pr-4">
        {card.currency ? <Badge variant="secondary">{card.currency}</Badge> : '—'}
      </td>
      <td className="whitespace-nowrap py-2.5 pr-4 text-xs text-muted-foreground">
        {card.feeNote ?? '—'}
      </td>
      <td className="py-2.5 text-right">
        <span className="inline-flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground hover:text-foreground"
            aria-label={t('accounts.cards.editTitle')}
            onClick={() => onEdit(card)}
          >
            <Pencil className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground hover:text-red-500"
            aria-label={t('accounts.cards.deleteLabel')}
            disabled={removing}
            onClick={() => onRemove(card)}
          >
            <X className="h-3.5 w-3.5" />
          </Button>
        </span>
      </td>
    </tr>
  );
}

export function PaymentCardsCard({ cards, accounts }: Readonly<{
  cards: PaymentCard[];
  accounts: Account[];
}>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingCard, setEditingCard] = useState<PaymentCard | null>(null);

  const deleteCard = useMutation({
    mutationFn: (id: string) => accountsApi.deleteCard(token, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cards'] }),
  });

  const openCreate = () => {
    setEditingCard(null);
    setDialogOpen(true);
  };

  const openEdit = (card: PaymentCard) => {
    setEditingCard(card);
    setDialogOpen(true);
  };

  const confirmRemove = (card: PaymentCard) => {
    if (globalThis.confirm(t('accounts.cards.confirmDelete', { name: card.name }))) {
      deleteCard.mutate(card.id);
    }
  };

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between space-y-0">
        <div className="space-y-1">
          <CardTitle className="text-base">{t('accounts.cards.title')}</CardTitle>
          <CardDescription className="text-xs">{t('accounts.cards.subtitle')}</CardDescription>
        </div>
        <Button variant="ghost" size="sm" onClick={openCreate}>
          <Plus className="h-4 w-4" />
          {t('accounts.cards.add')}
        </Button>
      </CardHeader>
      <CardContent>
        {cards.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">
            {t('accounts.cards.empty')}
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs text-muted-foreground">
                  <th className="py-2 pr-4 font-medium">{t('accounts.cards.card')}</th>
                  <th className="py-2 pr-4 font-medium">{t('accounts.cards.provider')}</th>
                  <th className="py-2 pr-4 font-medium">{t('accounts.cards.account')}</th>
                  <th className="py-2 pr-4 font-medium">{t('accounts.cards.currency')}</th>
                  <th className="py-2 pr-4 font-medium">{t('accounts.cards.fees')}</th>
                  <th className="py-2" aria-hidden="true" />
                </tr>
              </thead>
              <tbody>
                {cards.map((card) => (
                  <CardRow
                    key={card.id}
                    card={card}
                    onEdit={openEdit}
                    onRemove={confirmRemove}
                    removing={deleteCard.isPending}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
      {dialogOpen && (
        <CardDialog card={editingCard} accounts={accounts} onClose={() => setDialogOpen(false)} />
      )}
    </Card>
  );
}
