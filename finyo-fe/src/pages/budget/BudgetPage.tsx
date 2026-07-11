import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { budgetApi } from '@/api/budget';
import { FixedCostsCard } from './FixedCostsCard';
import { MonthlyBudgetCard } from './MonthlyBudgetCard';

function BudgetSkeleton() {
  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <Skeleton className="h-80 w-full lg:col-span-2" />
      <Skeleton className="h-80 w-full" />
    </div>
  );
}

export function BudgetPage() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const fixedCosts = useQuery({
    queryKey: ['fixed-costs'],
    queryFn: () => budgetApi.getFixedCosts(token),
    enabled: !!token,
  });

  const monthlyBudget = useQuery({
    queryKey: ['monthly-budget'],
    queryFn: () => budgetApi.getMonthlyBudget(token),
    enabled: !!token,
  });

  const isLoading = fixedCosts.isLoading || monthlyBudget.isLoading;
  const isError = fixedCosts.isError || monthlyBudget.isError;

  return (
    <div className="space-y-6">
      {isLoading && <BudgetSkeleton />}
      {isError && <p className="text-sm text-destructive">{t('budget.loadError')}</p>}

      {fixedCosts.data && monthlyBudget.data && (
        <div className="grid items-start gap-4 lg:grid-cols-3">
          <div className="lg:col-span-2">
            <FixedCostsCard list={fixedCosts.data} />
          </div>
          <MonthlyBudgetCard budget={monthlyBudget.data} />
        </div>
      )}
    </div>
  );
}
