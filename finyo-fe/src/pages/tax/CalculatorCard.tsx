import { useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Calculator, Save } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { ScenarioInlineRename } from '@/components/scenarios/ScenarioInlineRename';
import { useAuth } from '@/auth/useAuth';
import { pillar3Api } from '@/api/pillar3';
import { salaryApi } from '@/api/salary';
import { taxApi } from '@/api/tax';
import type {
  ChurchAffiliation,
  TaxCivilStatus,
  TaxCommune,
  TaxScenario,
  TaxYearInputs,
} from '@/api/tax';
import { useWealthOverview } from '@/hooks/financeQueries';
import { formatCHF } from '@/lib/formatters';

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

const CHURCH_OPTIONS: { value: ChurchAffiliation; labelKey: string }[] = [
  { value: 'NONE', labelKey: 'tax.church.none' },
  { value: 'PROTESTANT', labelKey: 'tax.church.protestant' },
  { value: 'ROMAN_CATHOLIC', labelKey: 'tax.church.catholic' },
];

const EMPTY_INPUTS: TaxYearInputs = {
  cantonCode: null,
  bfsNumber: null,
  civilStatus: null,
  churchAffiliation: null,
  numberOfChildren: null,
  grossEmploymentIncome: null,
  selfEmploymentIncome: null,
  investmentIncome: null,
  rentalIncome: null,
  deductionProfessionalExpenses: null,
  deductionInsurancePremiums: null,
  deductionCharitableDonations: null,
  deductionDebtInterest: null,
  pillar3aContribution: null,
  netWealth: null,
};

/** Multipliers arrive as decimals (1.44) and are displayed as percent (144 %). */
function formatMultiplier(multiplier: number): string {
  return `${(multiplier * 100).toFixed(0)} %`;
}

function resolveChurchMultiplier(
  commune: TaxCommune | null,
  church: ChurchAffiliation,
): number | null {
  if (!commune) return null;
  if (church === 'PROTESTANT') return commune.churchMultiplierProtestant;
  if (church === 'ROMAN_CATHOLIC') return commune.churchMultiplierRomanCatholic;
  return null;
}

interface CalculatorCardProps {
  year: number;
  inputs: TaxYearInputs | null;
  /**
   * The scenario whose inputs fill the form. While set, the save button
   * updates this scenario in place and all derived prefills are suppressed
   * so an update never absorbs cross-module values silently.
   */
  selectedScenario?: TaxScenario | null;
  /** Called after a successful recalculation (the page clears its scenario selection). */
  onCalculated?: () => void;
  /** Called after the selected scenario was updated (the page shows the saved flash). */
  onScenarioUpdated?: () => void;
  /**
   * When set, a save-scenario button is rendered next to the calculate button.
   * The current form is persisted first (same upsert as calculate) and the
   * callback fires only on success, so scenarios never snapshot stale inputs.
   */
  onSaveScenarioRequested?: () => void;
  /** Extra content rendered next to the calculate button (e.g. saved flash). */
  actionsExtra?: ReactNode;
  /** Rendered below the actions row (e.g. the inline scenario save panel). */
  footer?: ReactNode;
  className?: string;
}

export function CalculatorCard({
  year,
  inputs,
  selectedScenario,
  onCalculated,
  onScenarioUpdated,
  onSaveScenarioRequested,
  actionsExtra,
  footer,
  className,
}: Readonly<CalculatorCardProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [canton, setCanton] = useState(inputs?.cantonCode ?? 'SG');
  const [bfsNumber, setBfsNumber] = useState(
    inputs?.bfsNumber != null ? String(inputs.bfsNumber) : '',
  );
  const [civilStatus, setCivilStatus] = useState<TaxCivilStatus>(inputs?.civilStatus ?? 'SINGLE');
  const [church, setChurch] = useState<ChurchAffiliation>(inputs?.churchAffiliation ?? 'NONE');
  const [children, setChildren] = useState(
    inputs?.numberOfChildren != null ? String(inputs.numberOfChildren) : '0',
  );
  const [grossIncome, setGrossIncome] = useState(
    inputs?.grossEmploymentIncome != null ? String(inputs.grossEmploymentIncome) : '',
  );
  const [investmentIncome, setInvestmentIncome] = useState(
    inputs?.investmentIncome != null ? String(inputs.investmentIncome) : '',
  );
  const [pillar3a, setPillar3a] = useState(
    inputs?.pillar3aContribution != null ? String(inputs.pillar3aContribution) : '',
  );
  const [netWealth, setNetWealth] = useState(
    inputs?.netWealth != null ? String(inputs.netWealth) : '',
  );

  // Cross-module prefill: derived, never written into the field state, so
  // values persisted for the year or typed by the user are never overwritten.
  // Editing a field marks it touched, which removes the prefill and its hint.
  const [pillar3aTouched, setPillar3aTouched] = useState(false);
  const [grossIncomeTouched, setGrossIncomeTouched] = useState(false);
  const [netWealthTouched, setNetWealthTouched] = useState(false);
  const [pickedScenarioId, setPickedScenarioId] = useState('');

  const { data: communes } = useQuery({
    queryKey: ['tax', 'communes', canton, String(year)],
    queryFn: () => taxApi.getCommunes(token, canton, year),
    enabled: !!token && !!canton,
  });

  const { data: scenarios } = useQuery({
    queryKey: ['pillar3-scenarios'],
    queryFn: () => pillar3Api.getScenarios(token),
    enabled: !!token,
  });

  const { data: salary } = useQuery({
    queryKey: ['salary'],
    queryFn: () => salaryApi.get(token),
    enabled: !!token,
  });

  const { data: wealth } = useWealthOverview();

  const scenarioPrefill =
    !pillar3aTouched && !pillar3a && inputs?.pillar3aContribution == null && !selectedScenario
      ? scenarios?.find((scenario) => scenario.isDefault)
      : undefined;
  const pillar3aValue = scenarioPrefill
    ? String(scenarioPrefill.inputs.annualContribution)
    : pillar3a;

  const salaryPrefill =
    !grossIncomeTouched && !grossIncome && inputs?.grossEmploymentIncome == null
    && !selectedScenario && salary != null && salary.result.grossYearly > 0
      ? String(salary.result.grossYearly)
      : null;
  const grossIncomeValue = salaryPrefill ?? grossIncome;

  // Wealth without 3a buckets: pillar 3a assets are not taxable net wealth.
  const taxableWealth =
    wealth != null
      ? wealth.total
        - wealth.buckets
          .filter((bucket) => bucket.source === 'PILLAR3')
          .reduce((sum, bucket) => sum + bucket.balance, 0)
      : null;
  const wealthPrefill =
    !netWealthTouched && !netWealth && inputs?.netWealth == null
    && !selectedScenario && taxableWealth != null && taxableWealth > 0
      ? String(Math.round(taxableWealth))
      : null;
  const netWealthValue = wealthPrefill ?? netWealth;

  const selectedCommune = communes?.find((c) => String(c.bfsNumber) === bfsNumber) ?? null;
  const churchMultiplier = resolveChurchMultiplier(selectedCommune, church);

  // Fields controlled by the form; stored inputs are spread first so values
  // without form controls (self-employment, rental, deductions) survive.
  const formValues = () => ({
    cantonCode: canton,
    bfsNumber: bfsNumber ? Number(bfsNumber) : null,
    civilStatus,
    churchAffiliation: church,
    numberOfChildren: Number.parseInt(children) || 0,
    grossEmploymentIncome: Number.parseFloat(grossIncomeValue) || 0,
    investmentIncome: investmentIncome ? Number.parseFloat(investmentIncome) : null,
    pillar3aContribution: pillar3aValue ? Number.parseFloat(pillar3aValue) : null,
    netWealth: netWealthValue ? Number.parseFloat(netWealthValue) : null,
  });

  const { mutate, isPending, error } = useMutation({
    mutationFn: () => {
      const payload: TaxYearInputs = {
        ...(inputs ?? EMPTY_INPUTS),
        ...formValues(),
      };
      return taxApi.upsertYear(token, year, payload);
    },
    onSuccess: (detail) => {
      queryClient.setQueryData(['tax', 'year', String(year)], detail);
      queryClient.invalidateQueries({ queryKey: ['tax', 'years'] });
      onCalculated?.();
    },
  });

  // Updates the selected scenario in place — the year itself stays untouched.
  const updateScenario = useMutation({
    mutationFn: (scenario: TaxScenario) =>
      taxApi.updateScenario(token, year, scenario.id, {
        ...scenario.inputs,
        name: scenario.name,
        ...formValues(),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tax', 'scenarios', String(year)] });
      onScenarioUpdated?.();
    },
  });

  const changeCanton = (value: string) => {
    setCanton(value);
    setBfsNumber('');
  };

  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle className="text-base">{t('tax.title')}</CardTitle>
        {selectedScenario && (
          <ScenarioInlineRename
            name={selectedScenario.name}
            isDefault={selectedScenario.isDefault}
            renameLabel={t('tax.renameScenario')}
            queryKey={['tax', 'scenarios', String(year)]}
            // Renaming sends the stored inputs, never the live form state.
            onRename={(name) =>
              taxApi.updateScenario(token, year, selectedScenario.id, {
                ...selectedScenario.inputs,
                name,
              })
            }
          />
        )}
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
          <div className="space-y-2">
            <Label>{t('tax.canton')}</Label>
            <Select value={canton} onValueChange={changeCanton}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {CANTONS.map((c) => (
                  <SelectItem key={c} value={c}>{c}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>{t('tax.commune')}</Label>
            <Select value={bfsNumber || undefined} onValueChange={setBfsNumber}>
              <SelectTrigger><SelectValue placeholder={t('tax.commune')} /></SelectTrigger>
              <SelectContent>
                {(communes ?? []).map((c) => (
                  <SelectItem key={c.bfsNumber} value={String(c.bfsNumber)}>
                    {c.communeName}
                  </SelectItem>
                ))}
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
          <div className="space-y-2">
            <Label>{t('tax.children')}</Label>
            <Input type="number" value={children} onChange={(e) => setChildren(e.target.value)} min={0} max={20} />
          </div>
          <div className="space-y-2">
            <Label>{t('tax.churchAffiliation')}</Label>
            <Select value={church} onValueChange={(v) => setChurch(v as ChurchAffiliation)}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {CHURCH_OPTIONS.map((o) => (
                  <SelectItem key={o.value} value={o.value}>{t(o.labelKey)}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label>{t('tax.grossIncome')} (CHF) *</Label>
            <Input
              type="number"
              value={grossIncomeValue}
              onChange={(e) => {
                setGrossIncome(e.target.value);
                setGrossIncomeTouched(true);
              }}
              placeholder="100000"
            />
            {salaryPrefill != null && (
              <p className="text-xs text-muted-foreground">{t('tax.prefill.fromSalary')}</p>
            )}
          </div>
          <div className="space-y-2">
            <Label>{t('tax.investmentIncome')} (CHF)</Label>
            <Input type="number" value={investmentIncome} onChange={(e) => setInvestmentIncome(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>
              <Link to="/pillar3" className="underline-offset-2 hover:underline">
                {t('tax.pillar3')} ↗
              </Link>{' '}
              (CHF)
            </Label>
            <Input
              type="number"
              value={pillar3aValue}
              onChange={(e) => {
                setPillar3a(e.target.value);
                setPillar3aTouched(true);
              }}
              max={7258}
            />
            {scenarioPrefill && (
              <p className="text-xs text-muted-foreground">
                {t('tax.prefill.fromScenario', { name: scenarioPrefill.name })}
              </p>
            )}
            {scenarios && scenarios.length > 0 && (
              <Select
                value={pickedScenarioId || scenarioPrefill?.id || undefined}
                onValueChange={(id) => {
                  setPickedScenarioId(id);
                  const scenario = scenarios.find((s) => s.id === id);
                  if (scenario) {
                    setPillar3a(String(scenario.inputs.annualContribution));
                    setPillar3aTouched(true);
                  }
                }}
              >
                <SelectTrigger aria-label={t('tax.prefill.pickScenario')} className="h-8 text-xs">
                  <SelectValue placeholder={t('tax.prefill.pickScenario')} />
                </SelectTrigger>
                <SelectContent>
                  {scenarios.map((scenario) => (
                    <SelectItem key={scenario.id} value={scenario.id}>
                      {scenario.isDefault ? '★ ' : ''}
                      {scenario.name} · {formatCHF(scenario.inputs.annualContribution)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </div>
          <div className="space-y-2">
            <Label>{t('tax.netWealth')} (CHF)</Label>
            <Input
              type="number"
              value={netWealthValue}
              onChange={(e) => {
                setNetWealth(e.target.value);
                setNetWealthTouched(true);
              }}
            />
            {wealthPrefill != null && (
              <p className="text-xs text-muted-foreground">{t('tax.prefill.fromWealth')}</p>
            )}
          </div>
        </div>

        {selectedCommune && (
          <div className="mt-4 flex flex-wrap items-center gap-x-2 gap-y-1 rounded-md bg-muted px-3 py-2 text-sm">
            <span className="text-muted-foreground">{t('tax.taxMultiplier')}:</span>
            <span>
              {t('tax.multiplierCommune')} {formatMultiplier(selectedCommune.multiplier)}
            </span>
            {selectedCommune.cantonMultiplier != null && (
              <span>
                · {t('tax.multiplierCanton')} {formatMultiplier(selectedCommune.cantonMultiplier)}
              </span>
            )}
            {church !== 'NONE' && churchMultiplier != null && (
              <span>
                · {t('tax.multiplierChurch')} {formatMultiplier(churchMultiplier)}
              </span>
            )}
          </div>
        )}

        <div className="mt-4 space-y-2">
          <div className="flex flex-wrap items-center gap-3">
            <Button onClick={() => mutate()} disabled={!grossIncomeValue || isPending}>
              <Calculator className="mr-2 h-4 w-4" />
              {isPending ? t('common.loading') : t('tax.calculateForYear', { year })}
            </Button>
            {selectedScenario ? (
              <Button
                variant="outline"
                disabled={!grossIncomeValue || isPending || updateScenario.isPending}
                onClick={() => updateScenario.mutate(selectedScenario)}
              >
                <Save className="mr-2 h-4 w-4" />
                {updateScenario.isPending ? t('common.loading') : t('tax.updateScenario')}
              </Button>
            ) : (
              onSaveScenarioRequested && (
                <Button
                  variant="outline"
                  disabled={!grossIncomeValue || isPending}
                  onClick={() =>
                    mutate(undefined, { onSuccess: () => onSaveScenarioRequested() })
                  }
                >
                  <Save className="mr-2 h-4 w-4" />
                  {t('tax.saveScenario')}
                </Button>
              )
            )}
            {actionsExtra}
          </div>
          {error && <p className="text-sm text-destructive">{error.message}</p>}
          {updateScenario.error && (
            <p className="text-sm text-destructive">{updateScenario.error.message}</p>
          )}
        </div>
        {footer}
      </CardContent>
    </Card>
  );
}
