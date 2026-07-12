import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { wealthApi } from '@/api/wealth';
import { WealthKpiTiles } from './WealthKpiTiles';
import { BucketsCard } from './BucketsCard';
import { WealthChart } from './WealthChart';

const KPI_SKELETON_KEYS = ['total', 'ytd', 'rate', 'forecast'];

function WealthSkeleton() {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {KPI_SKELETON_KEYS.map((key) => (
          <Skeleton key={key} className="h-20 w-full" />
        ))}
      </div>
      <Skeleton className="h-64 w-full" />
      <Skeleton className="h-64 w-full" />
    </div>
  );
}

/** Wealth overview page — the page heading comes from the content-area breadcrumb. */
export function WealthPage() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const { data: overview, isLoading, isError } = useQuery({
    queryKey: ['wealth'],
    queryFn: () => wealthApi.getOverview(token),
    enabled: !!token,
  });

  return (
    <div className="space-y-6">
      {isLoading && <WealthSkeleton />}
      {isError && <p className="text-sm text-destructive">{t('wealth.loadError')}</p>}

      {overview && (
        <>
          <WealthKpiTiles overview={overview} />
          <BucketsCard overview={overview} />
          <WealthChart overview={overview} />
        </>
      )}
    </div>
  );
}
