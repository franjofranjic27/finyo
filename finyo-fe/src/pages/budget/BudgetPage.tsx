import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useAuth } from '@/auth/useAuth';
import { budgetApi } from '@/api/budget';
import { FixedCostsCard } from './FixedCostsCard';
import { MonthlyBudgetCard } from './MonthlyBudgetCard';
import { MonthTab } from './MonthTab';
import { SalaryTab } from './SalaryTab';

const TABS = ['plan', 'month', 'salary'] as const;
type BudgetTab = (typeof TABS)[number];

function isBudgetTab(value: string | null): value is BudgetTab {
  return TABS.includes(value as BudgetTab);
}

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

  // The active tab lives in the URL so /budget?tab=month deep-links work.
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  const tab: BudgetTab = isBudgetTab(tabParam) ? tabParam : 'plan';

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
    <Tabs
      value={tab}
      onValueChange={(value) =>
        setSearchParams(value === 'plan' ? {} : { tab: value }, { replace: true })
      }
    >
      {/* max-w + overflow keep the tab bar horizontally scrollable on narrow screens. */}
      <TabsList className="max-w-full justify-start overflow-x-auto">
        <TabsTrigger value="plan" className="shrink-0">
          {t('budget.tabs.plan')}
        </TabsTrigger>
        <TabsTrigger value="month" className="shrink-0">
          {t('budget.tabs.month')}
        </TabsTrigger>
        <TabsTrigger value="salary" className="shrink-0">
          {t('budget.tabs.salary')}
        </TabsTrigger>
      </TabsList>
      <TabsContent value="plan" className="mt-4">
        <div className="space-y-6">
          {isLoading && <BudgetSkeleton />}
          {isError && <p className="text-sm text-destructive">{t('budget.loadError')}</p>}

          {fixedCosts.data && monthlyBudget.data && (
            <div className="grid items-start gap-4 lg:grid-cols-3">
              <div className="lg:col-span-2">
                <FixedCostsCard list={fixedCosts.data} />
              </div>
              <MonthlyBudgetCard budget={monthlyBudget.data} fixedCosts={fixedCosts.data} />
            </div>
          )}
        </div>
      </TabsContent>
      <TabsContent value="month" className="mt-4">
        <MonthTab />
      </TabsContent>
      <TabsContent value="salary" className="mt-4">
        <SalaryTab />
      </TabsContent>
    </Tabs>
  );
}
