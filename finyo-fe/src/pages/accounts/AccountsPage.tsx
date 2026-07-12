import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Lock } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { accountsApi } from '@/api/accounts';
import { BankAccountsCard } from './BankAccountsCard';
import { PaymentCardsCard } from './PaymentCardsCard';

function AccountsSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-64 w-full" />
      <Skeleton className="h-40 w-full" />
    </div>
  );
}

export function AccountsPage() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const accountsQuery = useQuery({
    queryKey: ['accounts'],
    queryFn: () => accountsApi.getAll(token),
    enabled: !!token,
  });

  const cardsQuery = useQuery({
    queryKey: ['cards'],
    queryFn: () => accountsApi.getCards(token),
    enabled: !!token,
  });

  const isLoading = accountsQuery.isLoading || cardsQuery.isLoading;
  const isError = accountsQuery.isError || cardsQuery.isError;

  return (
    <div className="space-y-6">
      {isLoading && <AccountsSkeleton />}
      {isError && <p className="text-sm text-destructive">{t('accounts.loadError')}</p>}

      {accountsQuery.data && cardsQuery.data && (
        <>
          <BankAccountsCard accounts={accountsQuery.data} />
          <PaymentCardsCard cards={cardsQuery.data} accounts={accountsQuery.data} />
          <div className="flex items-center gap-3 rounded-xl border border-border px-4 py-3 text-xs text-muted-foreground">
            <Lock className="h-4 w-4 shrink-0" aria-hidden="true" />
            <p>{t('accounts.privacyNote')}</p>
          </div>
        </>
      )}
    </div>
  );
}
