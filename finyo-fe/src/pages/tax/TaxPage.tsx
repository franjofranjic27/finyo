import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { taxApi } from '@/api/tax';
import { formatDate } from '@/lib/formatters';
import { buildYearList, toIsoDate } from './taxYearUtils';
import { YearSidebar } from './YearSidebar';
import { YearStatusBadge } from './YearStatusBadge';
import { YearActionsMenu } from './YearActionsMenu';
import { CalculatorCard } from './CalculatorCard';
import { TaxResultSection } from './TaxResultSection';
import { MonthlyPaymentsCard } from './MonthlyPaymentsCard';
import { DeadlinesPaymentsSection } from './DeadlinesPaymentsSection';
import { YearComparisonCard } from './YearComparisonCard';

// Matches the backend rate-data validation (@Min(2020) on the year path variable).
const MIN_YEAR = 2020;

function resolveYear(param: string | undefined, currentYear: number): number {
  const parsed = Number(param);
  const isValid = Number.isInteger(parsed) && parsed >= MIN_YEAR && parsed <= currentYear;
  return isValid ? parsed : currentYear;
}

export function TaxPage() {
  const { t, i18n } = useTranslation();
  const { year: yearParam } = useParams();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const currentYear = new Date().getFullYear();
  const year = resolveYear(yearParam, currentYear);

  const { data: years } = useQuery({
    queryKey: ['tax', 'years'],
    queryFn: () => taxApi.getYears(token),
    enabled: !!token,
  });

  // 404 means the year has no record yet — show the calculator with defaults.
  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ['tax', 'year', String(year)],
    queryFn: () => taxApi.getYear(token, year),
    enabled: !!token,
    retry: false,
  });

  const entries = buildYearList(years ?? [], currentYear);

  return (
    <div className="flex gap-6">
      {/* Desktop year sidebar */}
      <aside className="hidden w-40 shrink-0 lg:block print:hidden">
        <div className="sticky top-6">
          <YearSidebar entries={entries} selectedYear={year} />
        </div>
      </aside>

      <div className="min-w-0 flex-1 space-y-6">
        {/* Mobile year chip strip */}
        <div className="lg:hidden print:hidden">
          <YearSidebar entries={entries} selectedYear={year} orientation="horizontal" />
        </div>

        {/* Screen header: year title, status badge and year actions */}
        <div className="flex items-center gap-3 print:hidden">
          <h1 className="text-2xl font-semibold">{t('tax.taxYear')} {year}</h1>
          {detail && (
            <>
              <YearStatusBadge status={detail.status} />
              <YearActionsMenu year={year} status={detail.status} />
            </>
          )}
        </div>

        {/* Print-only header */}
        <h1 className="hidden text-xl font-semibold print:block">
          {t('tax.printTitle', { year })}
          {detail?.calculation?.communeName && ` · ${detail.calculation.communeName}`}
          {` · ${formatDate(toIsoDate(new Date()), i18n.language)}`}
        </h1>

        {detailLoading ? (
          <TaxPageSkeleton />
        ) : (
          <>
            {!detail && (
              <p className="text-sm text-muted-foreground print:hidden">
                {t('tax.noYearData', { year })}
              </p>
            )}
            <CalculatorCard
              key={year}
              year={year}
              inputs={detail?.inputs ?? null}
              className="print:hidden"
            />
            {detail?.calculation && (
              <TaxResultSection result={detail.calculation} inputs={detail.inputs} />
            )}
            {detail && <MonthlyPaymentsCard detail={detail} className="print:hidden" />}
            {detail && (
              <DeadlinesPaymentsSection year={year} detail={detail} className="print:hidden" />
            )}
            <YearComparisonCard years={years ?? []} className="print:hidden" />
          </>
        )}
      </div>
    </div>
  );
}

function TaxPageSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-72 w-full" />
      <div className="grid gap-4 md:grid-cols-2">
        <Skeleton className="h-64 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    </div>
  );
}
