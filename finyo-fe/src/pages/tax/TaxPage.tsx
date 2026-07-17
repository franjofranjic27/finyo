import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useBreadcrumb } from '@/components/layout/BreadcrumbContext';
import { useAuth } from '@/auth/useAuth';
import { taxApi } from '@/api/tax';
import type { TaxYearDetail } from '@/api/tax';
import { formatDate } from '@/lib/formatters';
import { buildYearList, toIsoDate } from './taxYearUtils';
import { YearSidebar } from './YearSidebar';
import { YearStatusBadge } from './YearStatusBadge';
import { YearActionsMenu } from './YearActionsMenu';
import { TaxDocumentUploadDialog } from './TaxDocumentUploadDialog';
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

  // Year detail routes (/tax/2025) get a two-segment breadcrumb; the plain
  // /tax route keeps the single-segment fallback title.
  useBreadcrumb(
    yearParam
      ? [
          { label: t('nav.tax'), to: '/tax' },
          { label: t('breadcrumb.taxYear', { year }) },
        ]
      : null,
  );

  const [uploadOpen, setUploadOpen] = useState(false);

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
        <div className="flex flex-wrap items-center gap-3 print:hidden">
          <h1 className="text-2xl font-semibold">{t('tax.taxYear')} {year}</h1>
          {detail && (
            <>
              <YearStatusBadge status={detail.status} />
              <YearActionsMenu year={year} status={detail.status} />
              {/* Icon-only on mobile so the header row fits on 390 px screens. */}
              <Button
                variant="outline"
                size="sm"
                aria-label={t('tax.upload.button')}
                onClick={() => setUploadOpen(true)}
              >
                <Upload className="h-4 w-4 sm:mr-1" />
                <span className="hidden sm:inline">{t('tax.upload.button')}</span>
              </Button>
              <TaxDocumentUploadDialog
                year={year}
                inputs={detail.inputs}
                assessedAmount={detail.assessedAmount}
                open={uploadOpen}
                onOpenChange={setUploadOpen}
              />
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

  // Undefined = no interaction yet → the default scenario (if any) is shown;
  // null = explicitly deselected (the year's own calculation) and never
  // re-overridden by a refetch; string = an explicitly selected scenario.
  const [selection, setSelection] = useState<string | null | undefined>(undefined);
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

  const defaultScenario = scenarios?.find((s) => s.isDefault) ?? null;
  // Derived instead of set in an effect: before any interaction the default
  // scenario is preselected as soon as the list is loaded.
  const selectedScenarioId =
    selection === undefined ? (defaultScenario?.id ?? null) : selection;
  // A deleted scenario resolves to null and falls back to the year's result.
  const selectedScenario = scenarios?.find((s) => s.id === selectedScenarioId) ?? null;

  return (
    <>
      {!detail && (
        <p className="text-sm text-muted-foreground print:hidden">
          {t('tax.noYearData', { year })}
        </p>
      )}
      {/* Keyed by the selection so the whole form remounts with the scenario's
          (or the year's) inputs when a chip is toggled. */}
      <CalculatorCard
        key={selectedScenarioId ?? 'year'}
        year={year}
        inputs={selectedScenario ? selectedScenario.inputs : (detail?.inputs ?? null)}
        selectedScenario={selectedScenario}
        className="print:hidden"
        onCalculated={() => setSelection(null)}
        onScenarioUpdated={() => setSavedFlashCount((count) => count + 1)}
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
              isFirst={(scenarios ?? []).length === 0}
              onClose={() => setSavePanelOpen(false)}
              onSaved={(scenario) => {
                setSavePanelOpen(false);
                setSelection(scenario.id);
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
          onSelect={setSelection}
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
                onDeleted={() => setSelection(null)}
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
