import { useEffect, useState } from 'react';
import type { ChangeEvent, Dispatch, SetStateAction } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowUpRight, Check } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { useAuth } from '@/auth/useAuth';
import { salaryApi } from '@/api/salary';
import type {
  Salary, SalaryDeduction, SalaryInput, SalaryInputMode, SalaryResult,
} from '@/api/salary';
import { budgetApi } from '@/api/budget';
import type { MonthlyBudget } from '@/api/budget';
import { formatCHF, formatPercent } from '@/lib/formatters';

/** Rates keep their configured precision (e.g. 0.455%), unlike the 1-dp totals. */
const formatRate = (pct: number) =>
  `${pct.toLocaleString('en-US', { maximumFractionDigits: 3 })}%`;

const RATE_FIELDS = [
  { field: 'ahvPct', type: 'AHV' },
  { field: 'alvPct', type: 'ALV' },
  { field: 'nbuPct', type: 'NBU' },
  { field: 'ktgPct', type: 'KTG' },
] as const;

const FIXED_FIELDS = [
  { field: 'pensionFixed', labelKey: 'budget.salary.inputs.pension' },
  { field: 'otherFixed', labelKey: 'budget.salary.inputs.other' },
] as const;

/** Deduction types that carry an explanatory hint in the i18n bundle. */
const HINTED_TYPES = new Set<string>(['NBU', 'KTG']);

/** Percentage-based social insurance deductions; the rest are fixed amounts. */
const SOCIAL_TYPES = new Set<string>(['AHV', 'ALV', 'NBU', 'KTG']);

type NumericField =
  | (typeof RATE_FIELDS)[number]['field']
  | (typeof FIXED_FIELDS)[number]['field']
  | 'grossMonthly'
  | 'grossYearly';

interface SalaryFormState extends Record<NumericField, string> {
  inputMode: SalaryInputMode;
  thirteenthSalary: boolean;
}

function toFormState(salary: Salary): SalaryFormState {
  return {
    grossMonthly: String(salary.grossMonthly),
    grossYearly: String(salary.grossYearly),
    inputMode: salary.inputMode,
    thirteenthSalary: salary.thirteenthSalary,
    ahvPct: String(salary.ahvPct),
    alvPct: String(salary.alvPct),
    nbuPct: String(salary.nbuPct),
    ktgPct: String(salary.ktgPct),
    pensionFixed: String(salary.pensionFixed),
    otherFixed: String(salary.otherFixed),
  };
}

const roundToCents = (value: number) => Math.round(value * 100) / 100;

function toInput(form: SalaryFormState): SalaryInput {
  const num = (value: string) => Number.parseFloat(value) || 0;
  const monthsPerYear = form.thirteenthSalary ? 13 : 12;
  // The non-authoritative gross is derived from the entered one, so the
  // payload stays consistent regardless of stale values in the other field.
  const grossMonthly =
    form.inputMode === 'MONTHLY'
      ? num(form.grossMonthly)
      : roundToCents(num(form.grossYearly) / monthsPerYear);
  const grossYearly =
    form.inputMode === 'YEARLY'
      ? num(form.grossYearly)
      : roundToCents(num(form.grossMonthly) * monthsPerYear);
  return {
    grossMonthly,
    grossYearly,
    inputMode: form.inputMode,
    thirteenthSalary: form.thirteenthSalary,
    ahvPct: num(form.ahvPct),
    alvPct: num(form.alvPct),
    nbuPct: num(form.nbuPct),
    ktgPct: num(form.ktgPct),
    pensionFixed: num(form.pensionFixed),
    otherFixed: num(form.otherFixed),
  };
}

export function SalaryTab() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const query = useQuery({
    queryKey: ['salary'],
    queryFn: () => salaryApi.get(token),
    enabled: !!token,
  });

  if (query.isLoading) {
    return (
      <div className="grid items-start gap-4 lg:grid-cols-[360px_1fr]">
        <Skeleton className="h-96 w-full" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  if (query.isError) {
    return <p className="text-sm text-destructive">{t('budget.salary.loadError')}</p>;
  }

  if (!query.data) return null;

  return <SalaryCalculator salary={query.data} />;
}

function SalaryCalculator({ salary }: Readonly<{ salary: Salary }>) {
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  // Mounted only once data is available, so the form can seed from the server state.
  const [form, setForm] = useState<SalaryFormState>(() => toFormState(salary));

  const save = useMutation({
    mutationFn: (data: SalaryInput) => salaryApi.update(token, data),
    onSuccess: (updated) => queryClient.setQueryData(['salary'], updated),
  });

  return (
    <div className="grid items-start gap-4 lg:grid-cols-[360px_1fr]">
      <SalaryInputsCard
        form={form}
        setForm={setForm}
        onSubmit={() => save.mutate(toInput(form))}
        saving={save.isPending}
        error={save.error}
      />
      <SalaryResultCard result={salary.result} />
    </div>
  );
}

interface SalaryInputsCardProps {
  form: SalaryFormState;
  setForm: Dispatch<SetStateAction<SalaryFormState>>;
  onSubmit: () => void;
  saving: boolean;
  error: Error | null;
}

function SalaryInputsCard({ form, setForm, onSubmit, saving, error }: Readonly<SalaryInputsCardProps>) {
  const { t } = useTranslation();

  const setField = (field: NumericField) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const setInputMode = (inputMode: SalaryInputMode) =>
    setForm((prev) => ({ ...prev, inputMode }));

  const grossMonthly = Number.parseFloat(form.grossMonthly) || 0;
  const grossYearly = Number.parseFloat(form.grossYearly) || 0;
  const monthsPerYear = form.thirteenthSalary ? 13 : 12;
  const isYearly = form.inputMode === 'YEARLY';

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('budget.salary.inputs.title')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <Label>{t('budget.salary.inputs.mode')}</Label>
          <div className="flex gap-1">
            <Button
              variant={form.inputMode === 'MONTHLY' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setInputMode('MONTHLY')}
            >
              {t('budget.salary.inputs.modeMonthly')}
            </Button>
            <Button
              variant={form.inputMode === 'YEARLY' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setInputMode('YEARLY')}
            >
              {t('budget.salary.inputs.modeYearly')}
            </Button>
          </div>
        </div>

        {isYearly ? (
          <div className="space-y-2">
            <Label htmlFor="salary-grossYearly">
              {t('budget.salary.inputs.grossYearlyLabel')}
            </Label>
            <Input
              id="salary-grossYearly"
              type="number"
              min={0}
              step={0.01}
              value={form.grossYearly}
              onChange={setField('grossYearly')}
            />
            <p className="text-xs text-muted-foreground">
              {t('budget.salary.inputs.monthlyDerived', {
                amount: formatCHF(grossYearly / monthsPerYear),
              })}
            </p>
          </div>
        ) : (
          <div className="space-y-2">
            <Label htmlFor="salary-grossMonthly">{t('budget.salary.inputs.grossMonthly')}</Label>
            <Input
              id="salary-grossMonthly"
              type="number"
              min={0}
              step={0.01}
              value={form.grossMonthly}
              onChange={setField('grossMonthly')}
            />
            <p className="text-xs text-muted-foreground">
              {t('budget.salary.inputs.grossYearly', {
                amount: formatCHF(grossMonthly * monthsPerYear),
              })}
            </p>
          </div>
        )}

        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <Checkbox
              id="salary-thirteenth"
              checked={form.thirteenthSalary}
              onCheckedChange={(checked) =>
                setForm((prev) => ({ ...prev, thirteenthSalary: checked === true }))
              }
            />
            <Label htmlFor="salary-thirteenth">{t('budget.salary.inputs.thirteenth')}</Label>
          </div>
          {isYearly && form.thirteenthSalary && (
            <p className="text-xs text-muted-foreground">
              {t('budget.salary.inputs.yearlyIncludesThirteenth')}
            </p>
          )}
        </div>

        <div className="space-y-4 border-t border-border pt-4">
          <h4 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {t('budget.salary.inputs.ratesTitle')}
          </h4>
          <div className="grid grid-cols-2 gap-4">
            {RATE_FIELDS.map(({ field, type }) => (
              <div key={field} className="space-y-2">
                <Label htmlFor={`salary-${field}`}>{t(`budget.salary.deduction.${type}`)}</Label>
                <Input
                  id={`salary-${field}`}
                  type="number"
                  min={0}
                  max={100}
                  step={0.001}
                  value={form[field]}
                  onChange={setField(field)}
                />
                {HINTED_TYPES.has(type) && (
                  <p className="text-xs text-muted-foreground">
                    {t(`budget.salary.deductionHint.${type}`)}
                  </p>
                )}
              </div>
            ))}
            {FIXED_FIELDS.map(({ field, labelKey }) => (
              <div key={field} className="space-y-2">
                <Label htmlFor={`salary-${field}`}>{t(labelKey)}</Label>
                <Input
                  id={`salary-${field}`}
                  type="number"
                  min={0}
                  step={0.01}
                  value={form[field]}
                  onChange={setField(field)}
                />
              </div>
            ))}
          </div>
        </div>

        {/* Server errors, e.g. the RFC-7807 detail on validation failures. */}
        {error && <p className="text-sm text-destructive">{error.message}</p>}

        <Button className="w-full" onClick={onSubmit} disabled={saving}>
          {saving ? t('common.loading') : t('budget.salary.inputs.submit')}
        </Button>
      </CardContent>
    </Card>
  );
}

function SalaryResultCard({ result }: Readonly<{ result: SalaryResult }>) {
  const { t } = useTranslation();

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('budget.salary.result.title')}</CardTitle>
        <p className="text-xs text-muted-foreground">{t('budget.salary.result.subtitle')}</p>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('budget.salary.result.position')}</TableHead>
              <TableHead className="text-right">{t('budget.salary.result.rate')}</TableHead>
              <TableHead className="text-right">{t('budget.salary.result.perMonth')}</TableHead>
              <TableHead className="text-right">{t('budget.salary.result.perYear')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="tabular-nums">
            <TableRow className="bg-secondary/50 font-semibold">
              <TableCell>{t('budget.salary.result.gross')}</TableCell>
              <TableCell />
              <TableCell className="text-right">{formatCHF(result.grossMonthly)}</TableCell>
              <TableCell className="text-right">{formatCHF(result.grossYearly)}</TableCell>
            </TableRow>
            {result.deductions.map((deduction) => (
              <DeductionRow key={deduction.type} deduction={deduction} />
            ))}
            <TableRow className="bg-secondary/50 font-semibold">
              <TableCell>{t('budget.salary.result.totalDeductions')}</TableCell>
              <TableCell className="text-right">
                {formatPercent(result.totalDeductionsPct)}
              </TableCell>
              <TableCell className="text-right text-red-500">
                −{formatCHF(result.totalPerMonth)}
              </TableCell>
              <TableCell className="text-right text-red-500">
                −{formatCHF(result.totalPerYear)}
              </TableCell>
            </TableRow>
            <TableRow className="font-bold text-emerald-500">
              <TableCell>{t('budget.salary.result.net')}</TableCell>
              <TableCell />
              <TableCell className="text-right">{formatCHF(result.netMonthly)}</TableCell>
              <TableCell className="text-right">{formatCHF(result.netYearly)}</TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <DistributionBar result={result} />
        <ApplyNetButton result={result} />
      </CardContent>
    </Card>
  );
}

function DeductionRow({ deduction }: Readonly<{ deduction: SalaryDeduction }>) {
  const { t } = useTranslation();

  return (
    <TableRow>
      <TableCell>{t(`budget.salary.deduction.${deduction.type}`)}</TableCell>
      <TableCell className="text-right text-muted-foreground">
        {deduction.pct === null ? t('budget.salary.result.fixed') : formatRate(deduction.pct)}
      </TableCell>
      <TableCell className="text-right text-red-500">−{formatCHF(deduction.perMonth)}</TableCell>
      <TableCell className="text-right text-red-500">−{formatCHF(deduction.perYear)}</TableCell>
    </TableRow>
  );
}

function DistributionBar({ result }: Readonly<{ result: SalaryResult }>) {
  const { t } = useTranslation();

  if (result.grossMonthly === 0) return null;

  const sumPerMonth = (social: boolean) =>
    result.deductions
      .filter((d) => SOCIAL_TYPES.has(d.type) === social)
      .reduce((sum, d) => sum + d.perMonth, 0);

  const share = (value: number) => (value / result.grossMonthly) * 100;

  const segments = [
    { key: 'net', labelKey: 'budget.salary.result.legendNet', pct: share(result.netMonthly), colour: 'bg-emerald-500' },
    { key: 'social', labelKey: 'budget.salary.result.legendSocial', pct: share(sumPerMonth(true)), colour: 'bg-amber-500' },
    { key: 'fixed', labelKey: 'budget.salary.result.legendFixed', pct: share(sumPerMonth(false)), colour: 'bg-violet-500' },
  ];

  return (
    <div className="mt-5 space-y-2">
      <div className="flex h-4 overflow-hidden rounded-full">
        {segments.map((segment) => (
          <div
            key={segment.key}
            className={segment.colour}
            style={{ width: `${segment.pct}%` }}
            aria-hidden="true"
          />
        ))}
      </div>
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
        {segments.map((segment) => (
          <span key={segment.key} className="flex items-center gap-1.5">
            <span className={`h-2.5 w-2.5 rounded-sm ${segment.colour}`} aria-hidden="true" />
            {`${t(segment.labelKey)} ${formatPercent(segment.pct)}`}
          </span>
        ))}
      </div>
    </div>
  );
}

function ApplyNetButton({ result }: Readonly<{ result: SalaryResult }>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();
  const [applied, setApplied] = useState(false);

  useEffect(() => {
    if (!applied) return undefined;
    const timer = setTimeout(() => setApplied(false), 2000);
    return () => clearTimeout(timer);
  }, [applied]);

  // The apply action must preserve the five allocation fields of the budget.
  const monthlyBudget = useQuery({
    queryKey: ['monthly-budget'],
    queryFn: () => budgetApi.getMonthlyBudget(token),
    enabled: !!token,
  });

  const apply = useMutation({
    mutationFn: (current: MonthlyBudget) =>
      budgetApi.updateMonthlyBudget(token, {
        netIncome: result.netMonthly,
        savings: current.savings,
        investing: current.investing,
        pillar3a: current.pillar3a,
        taxReserve: current.taxReserve,
        workCosts: current.workCosts,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['monthly-budget'] });
      setApplied(true);
    },
  });

  const current = monthlyBudget.data;
  const disabled = !current || apply.isPending || result.grossMonthly === 0;

  const handleApply = () => {
    if (current) apply.mutate(current);
  };

  return (
    <Button variant="ghost" size="sm" className="mt-4" disabled={disabled} onClick={handleApply}>
      {applied ? (
        <>
          <Check className="mr-1 h-4 w-4 text-emerald-500" />
          {t('budget.salary.result.applied')}
        </>
      ) : (
        <>
          <ArrowUpRight className="mr-1 h-4 w-4" />
          {t('budget.salary.result.apply')}
        </>
      )}
    </Button>
  );
}
