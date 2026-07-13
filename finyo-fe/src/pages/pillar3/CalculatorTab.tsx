import { useState } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  AreaChart, Area, Tooltip, ResponsiveContainer, XAxis, YAxis, CartesianGrid, Legend,
} from 'recharts';
import { Calculator, X } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { taxApi } from '@/api/tax';
import type { Pillar3Result, TaxCivilStatus } from '@/api/tax';
import { pillar3Api } from '@/api/pillar3';
import type { Pillar3Product, Pillar3Scenario, Pillar3ScenarioInputs } from '@/api/pillar3';
import { PROFILE_QUERY_KEY, profileApi } from '@/api/profile';
import { formatCHF } from '@/lib/formatters';
import {
  chartAxisProps,
  chartGridProps,
  chartTooltipStyle,
  formatThousandsTick,
  formatTooltipCHF,
  renderLegendText,
} from '@/components/charts/chartStyle';
import { ProductSearchCombobox } from './ProductSearchCombobox';
import { ScenarioActionsMenu } from './ScenarioActionsMenu';
import { ScenarioBar } from './ScenarioBar';
import { ScenarioSavePanel } from './ScenarioSavePanel';

const CANTONS = [
  'AG','AI','AR','BE','BL','BS','FR','GE','GL','GR',
  'JU','LU','NE','NW','OW','SG','SH','SO','SZ','TG',
  'TI','UR','VD','VS','ZG','ZH',
];

const CIVIL_STATUS_OPTIONS: { value: TaxCivilStatus; labelKey: string }[] = [
  { value: 'SINGLE', labelKey: 'tax.civilStatus.single' },
  { value: 'MARRIED', labelKey: 'tax.civilStatus.married' },
  { value: 'SINGLE_PARENT', labelKey: 'tax.civilStatus.singleParent' },
];

const DEFAULT_YEARS = '30';

export function CalculatorTab() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const currentYear = new Date().getFullYear();
  const [balance, setBalance] = useState('');
  const [contribution, setContribution] = useState('7258');
  const [returnPct, setReturnPct] = useState('5.0');
  const [years, setYears] = useState(DEFAULT_YEARS);
  const [yearsTouched, setYearsTouched] = useState(false);
  const [income, setIncome] = useState('');
  const [canton, setCanton] = useState('SG');
  const [civilStatus, setCivilStatus] = useState<TaxCivilStatus>('SINGLE');
  const [selectedProduct, setSelectedProduct] = useState<Pillar3Product | null>(null);

  // Null = no scenario chip active, show the fresh calculation result.
  const [selectedScenarioId, setSelectedScenarioId] = useState<string | null>(null);
  const [savePanelOpen, setSavePanelOpen] = useState(false);

  const { data: products } = useQuery({
    queryKey: ['pillar3-products'],
    queryFn: () => pillar3Api.getProducts(token),
    enabled: !!token,
  });

  const { data: scenarios } = useQuery({
    queryKey: ['pillar3-scenarios'],
    queryFn: () => pillar3Api.getScenarios(token),
    enabled: !!token,
  });

  const { data: profile } = useQuery({
    queryKey: PROFILE_QUERY_KEY,
    queryFn: () => profileApi.get(token),
    enabled: !!token,
  });

  // Horizon prefill from the profile's retirement projection: derived, so it
  // applies only while the field still holds its untouched default and never
  // replaces a user-typed or scenario-loaded value.
  const profileYears =
    !yearsTouched && years === DEFAULT_YEARS && profile?.yearsToRetirement != null
      ? String(profile.yearsToRetirement)
      : null;
  const yearsValue = profileYears ?? years;

  const changeYears = (value: string) => {
    setYears(value);
    setYearsTouched(true);
  };

  // A deleted scenario resolves to null and falls back to the fresh result.
  const selectedScenario = scenarios?.find((s) => s.id === selectedScenarioId) ?? null;
  const hasDefault = scenarios?.some((s) => s.isDefault) ?? false;

  const scenarioInputs: Pillar3ScenarioInputs = {
    currentBalance: Number.parseFloat(balance) || 0,
    annualContribution: Number.parseFloat(contribution) || 0,
    assumedAnnualReturnPercent: Number.parseFloat(returnPct) || 5,
    yearsToRetirement: Number.parseInt(yearsValue) || 30,
    grossEmploymentIncome: income ? Number.parseFloat(income) : null,
    civilStatus: income ? civilStatus : null,
    cantonCode: income ? canton : null,
    taxYear: currentYear,
    productId: selectedProduct?.id ?? null,
  };

  const { mutate, data: result, isPending } = useMutation({
    // The extra `productId` in the scenario shape is ignored by the calculate endpoint.
    mutationFn: () => taxApi.calculatePillar3(token, scenarioInputs),
    // A fresh calculation always replaces an active scenario chip.
    onSuccess: () => setSelectedScenarioId(null),
  });

  const pickProduct = (product: Pillar3Product) => {
    setSelectedProduct(product);
    setReturnPct(String(product.netReturnPct));
  };

  const applyScenario = (scenario: Pillar3Scenario) => {
    const { inputs } = scenario;
    setBalance(String(inputs.currentBalance));
    setContribution(String(inputs.annualContribution));
    setReturnPct(String(scenario.effectiveReturnPercent));
    changeYears(String(inputs.yearsToRetirement));
    setIncome(inputs.grossEmploymentIncome != null ? String(inputs.grossEmploymentIncome) : '');
    if (inputs.cantonCode) setCanton(inputs.cantonCode);
    if (inputs.civilStatus) setCivilStatus(inputs.civilStatus);
    // The product may have been deleted since the scenario was saved.
    setSelectedProduct(products?.find((p) => p.id === inputs.productId) ?? null);
  };

  const selectScenario = (scenarioId: string | null) => {
    setSelectedScenarioId(scenarioId);
    const scenario = scenarioId ? scenarios?.find((s) => s.id === scenarioId) : null;
    if (scenario) applyScenario(scenario);
  };

  const displayedResult = selectedScenario ? selectedScenario.calculation : result;
  const displayedYears = selectedScenario
    ? selectedScenario.inputs.yearsToRetirement
    : Number.parseInt(yearsValue);

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('pillar3.title')}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
            <div className="space-y-2">
              <Label>{t('pillar3.currentBalance')} (CHF)</Label>
              <Input type="number" value={balance} onChange={(e) => setBalance(e.target.value)} placeholder="0" />
            </div>
            <div className="space-y-2">
              <Label>{t('pillar3.contribution')} (CHF, max 7'258)</Label>
              <Input type="number" value={contribution} onChange={(e) => setContribution(e.target.value)} max={7258} />
            </div>
            <div className="space-y-2">
              <Label>{t('pillar3.returnRate')}</Label>
              <Input
                type="number"
                value={returnPct}
                onChange={(e) => setReturnPct(e.target.value)}
                step={0.1}
                min={0}
                max={20}
                disabled={selectedProduct != null}
              />
            </div>
            <div className="space-y-2">
              <Label>{t('pillar3.yearsToRetirement')}</Label>
              <Input type="number" value={yearsValue} onChange={(e) => changeYears(e.target.value)} min={1} max={50} />
              {profileYears != null && (
                <p className="text-xs text-muted-foreground">{t('pillar3.prefilledFromProfile')}</p>
              )}
            </div>
            <div className="space-y-2 col-span-2 md:col-span-1">
              <Label>{t('pillar3.grossIncomeForSaving')}</Label>
              <Input type="number" value={income} onChange={(e) => setIncome(e.target.value)} placeholder="Optional" />
            </div>
            {income && (
              <>
                <div className="space-y-2">
                  <Label>{t('tax.canton')}</Label>
                  <Select value={canton} onValueChange={setCanton}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {CANTONS.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>{t('tax.civilStatus.label')}</Label>
                  <Select value={civilStatus} onValueChange={(v) => setCivilStatus(v as TaxCivilStatus)}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {CIVIL_STATUS_OPTIONS.map((o) => (
                        <SelectItem key={o.value} value={o.value}>{t(o.labelKey)}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </>
            )}
          </div>

          {/* Optional product picker: the product's net return drives the calculation. */}
          <div className="mt-4 space-y-2">
            <Label>{t('pillar3.scenarios.product')}</Label>
            <ProductSearchCombobox
              products={products ?? []}
              selectedIds={selectedProduct ? [selectedProduct.id] : []}
              onSelect={pickProduct}
            />
            {selectedProduct && (
              <span className="inline-flex items-center gap-2 rounded-full border bg-secondary py-1 pl-3.5 pr-1.5 text-[13px] font-medium">
                <span className="max-w-64 truncate">{selectedProduct.name}</span>
                <span className="truncate text-[11px] text-muted-foreground">
                  {selectedProduct.provider} · {selectedProduct.netReturnPct.toFixed(1)} %
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-6 w-6 text-muted-foreground hover:text-destructive"
                  aria-label={t('pillar3.scenarios.removeProduct')}
                  onClick={() => setSelectedProduct(null)}
                >
                  <X className="h-3.5 w-3.5" />
                </Button>
              </span>
            )}
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-2">
            <Button onClick={() => mutate()} disabled={isPending}>
              <Calculator className="mr-2 h-4 w-4" />
              {isPending ? t('common.loading') : t('tax.calculate')}
            </Button>
            <Button variant="outline" onClick={() => setSavePanelOpen(true)}>
              {t('pillar3.scenarios.save')}
            </Button>
          </div>

          {savePanelOpen && (
            <ScenarioSavePanel
              inputs={scenarioInputs}
              hasDefault={hasDefault}
              onClose={() => setSavePanelOpen(false)}
              onSaved={(scenario) => {
                setSavePanelOpen(false);
                setSelectedScenarioId(scenario.id);
              }}
            />
          )}
        </CardContent>
      </Card>

      <ScenarioBar
        scenarios={scenarios ?? []}
        selectedScenarioId={selectedScenarioId}
        onSelect={selectScenario}
        onAdd={() => setSavePanelOpen(true)}
      />

      {isPending && <Skeleton className="h-96 w-full" />}
      {displayedResult && (
        <div className="space-y-4">
          {selectedScenario && (
            <div className="flex items-center justify-between">
              <p className="text-sm font-semibold">
                {selectedScenario.isDefault && (
                  <span aria-hidden="true" className="mr-1 text-amber-500">★</span>
                )}
                {selectedScenario.name}
              </p>
              <ScenarioActionsMenu
                scenario={selectedScenario}
                onDeleted={() => setSelectedScenarioId(null)}
              />
            </div>
          )}
          <Pillar3ResultSection result={displayedResult} years={displayedYears} />
        </div>
      )}
    </div>
  );
}

function Pillar3ResultSection({ result, years }: Readonly<{ result: Pillar3Result; years: number }>) {
  const { t } = useTranslation();

  const chartData = result.yearlyProjection
    .filter((_, i) => i < 5 || (i + 1) % 5 === 0 || i === result.yearlyProjection.length - 1)
    .map((p) => ({
      year: String(p.year),
      // Start balance + paid-in contributions, so both areas stack up to the exact balance.
      Contributions: Math.round(p.balance - p.totalReturns),
      Returns: Math.round(p.totalReturns),
    }));

  return (
    <div className="space-y-4">
      {/* Key metrics */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <MetricCard label={t('pillar3.projectedBalance')} value={formatCHF(result.projectedBalanceAtRetirement)} />
        {result.annualTaxSaving > 0 && (
          <MetricCard label={t('pillar3.taxSaving')} value={formatCHF(result.annualTaxSaving)} highlight />
        )}
        <MetricCard label={t('pillar3.payoutTax')} value={formatCHF(result.estimatedPayoutTax)} />
        <MetricCard label={t('pillar3.netValue')} value={formatCHF(result.netValueAfterPayoutTax)} />
      </div>

      {/* Contribution cap info */}
      {!result.isMaxContribution && (
        <Card className="border-amber-500/30 bg-amber-500/5">
          <CardContent className="pt-4 text-sm text-amber-600 dark:text-amber-400">
            <Trans
              i18nKey="pillar3.contributionCap"
              values={{
                remaining: formatCHF(result.remainingUntilMax),
                max: formatCHF(result.maxContribution),
                saving: formatCHF(result.taxSavingIfMaxContribution - result.annualTaxSaving),
              }}
              components={{ strong: <strong /> }}
            />
          </CardContent>
        </Card>
      )}

      {/* Growth chart */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('pillar3.balanceGrowth', { years })}</CardTitle>
        </CardHeader>
        <CardContent>
          <ResponsiveContainer width="100%" height={280}>
            <AreaChart data={chartData}>
              <CartesianGrid {...chartGridProps} />
              <XAxis dataKey="year" {...chartAxisProps} />
              <YAxis {...chartAxisProps} tickFormatter={formatThousandsTick} />
              <Tooltip formatter={formatTooltipCHF} contentStyle={chartTooltipStyle} />
              <Legend formatter={renderLegendText} />
              <Area type="monotone" dataKey="Contributions" stackId="1" stroke="#6366f1" fill="#6366f1" fillOpacity={0.6} />
              <Area type="monotone" dataKey="Returns" stackId="1" stroke="#10b981" fill="#10b981" fillOpacity={0.6} />
            </AreaChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>

      {/* vs taxable savings */}
      {result.equivalentTaxableSavings > 0 && (
        <Card>
          <CardContent className="pt-4 text-sm space-y-1">
            <p className="font-medium">{t('pillar3.vsSavingsTitle')}</p>
            <p className="text-muted-foreground">
              <Trans
                i18nKey="pillar3.vsSavingsText"
                values={{
                  equivalent: formatCHF(result.equivalentTaxableSavings),
                  advantage: formatCHF(result.netValueAfterPayoutTax - result.equivalentTaxableSavings),
                }}
                components={{ strong: <strong />, emph: <strong className="text-emerald-500" /> }}
              />
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function MetricCard({ label, value, highlight }: Readonly<{ label: string; value: string; highlight?: boolean }>) {
  return (
    <Card className={highlight ? 'border-emerald-500/30 bg-emerald-500/5' : ''}>
      <CardContent className="pt-4">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className={`text-xl font-bold mt-1 ${highlight ? 'text-emerald-500' : ''}`}>{value}</p>
      </CardContent>
    </Card>
  );
}
