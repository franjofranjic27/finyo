import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { portfolioApi } from '@/api/portfolio';
import { KpiTiles } from './KpiTiles';
import { AddPositionCard } from './AddPositionCard';
import { PositionsTable } from './PositionsTable';
import { AllocationDonut } from './AllocationDonut';
import { PerformanceChart } from './PerformanceChart';

const KPI_SKELETON_KEYS = ['total', 'invested', 'gainloss', 'return'];

function PortfolioSkeleton() {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {KPI_SKELETON_KEYS.map((key) => (
          <Skeleton key={key} className="h-20 w-full" />
        ))}
      </div>
      <Skeleton className="h-48 w-full" />
      <Skeleton className="h-64 w-full" />
    </div>
  );
}

export function PortfolioPage() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const { data: portfolio, isLoading, isError } = useQuery({
    queryKey: ['portfolio'],
    queryFn: () => portfolioApi.getPortfolio(token),
    enabled: !!token,
  });

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">{t('investments.title')}</h1>

      {isLoading && <PortfolioSkeleton />}
      {isError && <p className="text-sm text-destructive">{t('investments.loadError')}</p>}

      {portfolio && (
        <>
          <KpiTiles portfolio={portfolio} />
          <AddPositionCard />
          <PositionsTable positions={portfolio.positions} />
          <div className="grid gap-4 lg:grid-cols-2">
            <AllocationDonut positions={portfolio.positions} />
            <PerformanceChart />
          </div>
        </>
      )}
    </div>
  );
}
