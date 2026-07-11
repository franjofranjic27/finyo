import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { taxApi } from '@/api/tax';
import type { TaxYearDetail } from '@/api/tax';
import { formatDate } from '@/lib/formatters';
import { buildYearList, toIsoDate } from './taxYearUtils';
import { YearSidebar } from './YearSidebar';
import { YearStatusBadge } from './YearStatusBadge';
import { YearActionsMenu } from './YearActionsMenu';
import { CalculatorCard } from './CalculatorCard';
import { TaxResultSection } from './TaxResultSection';
import { MonthlyPaymentsCard } from './MonthlyPaymentsCard';
import { DeadlinesPaymentsSection } from './DeadlinesPaymentsSection';
import { ScenarioBar } from './ScenarioBar';
import { ScenarioSavePanel } from './ScenarioSavePanel';
import { ScenarioActionsMenu } from './ScenarioActionsMenu';
import { YearComparisonCard } from './YearComparisonCard';

// Matches the backend rate-data validation (@Min(2020) on the year path variable).
const MIN_YEAR = 2020;

const SAVED_FLASH_MS = 2400;

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
            {/* Keyed by year so scenario selection and save-panel state reset on year change */}
            <TaxYearContent key={year} year={year} detail={detail ?? null} />
            <YearComparisonCard years={years ?? []} className="print:hidden" />
          </>
        )}
      </div>
    </div>
  );
}

interface TaxYearContentProps {
  year: number;
  detail: TaxYearDetail | null;
}

/** Calculator, scenario bar, results and payment sections for one tax year. */
function TaxYearContent({ year, detail }: Readonly<TaxYearContentProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  // Null = no scenario chip active, show the year's own current calculation.
  const [selectedScenarioId, setSelectedScenarioId] = useState<string | null>(null);
  const [savePanelOpen, setSavePanelOpen] = useState(false);
  // Counter instead of a boolean so a save while the flash is still visible
  // restarts the timeout (the effect re-runs on every increment).
  const [savedFlashCount, setSavedFlashCount] = useState(0);
  const savedVisible = savedFlashCount > 0;

  useEffect(() => {
    if (savedFlashCount === 0) return;
    const timer = setTimeout(() => setSavedFlashCount(0), SAVED_FLASH_MS);
    return () => clearTimeout(timer);
  }, [savedFlashCount]);

  const { data: scenarios } = useQuery({
    queryKey: ['tax', 'scenarios', String(year)],
    queryFn: () => taxApi.getScenarios(token, year),
    enabled: !!token,
  });

  // A deleted scenario resolves to null and falls back to the year's result.
  const selectedScenario = scenarios?.find((s) => s.id === selectedScenarioId) ?? null;
  const defaultScenario = scenarios?.find((s) => s.isDefault) ?? null;

  return (
    <>
      {!detail && (
        <p className="text-sm text-muted-foreground print:hidden">
          {t('tax.noYearData', { year })}
        </p>
      )}
      <CalculatorCard
        year={year}
        inputs={detail?.inputs ?? null}
        className="print:hidden"
        onCalculated={() => setSelectedScenarioId(null)}
        // Persists the current form first so the panel never snapshots stale
        // inputs; the panel below reads detail.inputs from the updated query.
        onSaveScenarioRequested={() => setSavePanelOpen(true)}
        actionsExtra={
          savedVisible && (
            <span className="inline-flex items-center gap-1 text-[13px] font-semibold text-emerald-500">
              ✓ {t('tax.scenarioSaved')}
            </span>
          )
        }
        footer={
          savePanelOpen && detail?.inputs ? (
            <ScenarioSavePanel
              year={year}
              inputs={detail.inputs}
              hasDefault={defaultScenario != null}
              onClose={() => setSavePanelOpen(false)}
              onSaved={(scenario) => {
                setSavePanelOpen(false);
                setSelectedScenarioId(scenario.id);
                setSavedFlashCount((count) => count + 1);
              }}
            />
          ) : undefined
        }
      />
      {detail && (
        <ScenarioBar
          scenarios={scenarios ?? []}
          selectedScenarioId={selectedScenarioId}
          onSelect={setSelectedScenarioId}
          onAdd={() => setSavePanelOpen(true)}
          addDisabled={!detail.inputs}
          className="print:hidden"
        />
      )}
      {(detail?.calculation || selectedScenario) && (
        <TaxResultSection
          result={detail?.calculation ?? null}
          inputs={detail?.inputs ?? null}
          scenario={selectedScenario}
          defaultScenario={defaultScenario}
          scenarioActions={
            selectedScenario && (
              <ScenarioActionsMenu
                year={year}
                scenario={selectedScenario}
                onDeleted={() => setSelectedScenarioId(null)}
              />
            )
          }
        />
      )}
      {detail && <MonthlyPaymentsCard detail={detail} className="print:hidden" />}
      {detail && (
        <DeadlinesPaymentsSection year={year} detail={detail} className="print:hidden" />
      )}
    </>
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
