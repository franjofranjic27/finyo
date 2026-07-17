import { useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { ChevronRight, Printer } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import {
  Collapsible, CollapsibleContent, CollapsibleTrigger,
} from '@/components/ui/collapsible';
import type { TaxResultResponse, TaxScenario, TaxYearInputs } from '@/api/tax';
import { formatCHF, formatPercent } from '@/lib/formatters';
import { cn } from '@/lib/utils';

interface DeductionRow {
  labelKey: string;
  amount: number;
}

/** The deduction positions actually entered by the user, non-zero only. */
function buildDeductionRows(inputs: TaxYearInputs | null): DeductionRow[] {
  const positions: { labelKey: string; amount: number | null }[] = [
    { labelKey: 'tax.deduction.professionalExpenses', amount: inputs?.deductionProfessionalExpenses ?? null },
    { labelKey: 'tax.deduction.insurancePremiums', amount: inputs?.deductionInsurancePremiums ?? null },
    { labelKey: 'tax.deduction.charitableDonations', amount: inputs?.deductionCharitableDonations ?? null },
    { labelKey: 'tax.deduction.debtInterest', amount: inputs?.deductionDebtInterest ?? null },
    { labelKey: 'tax.deduction.pillar3a', amount: inputs?.pillar3aContribution ?? null },
  ];
  return positions.flatMap((p) =>
    p.amount != null && p.amount > 0 ? [{ labelKey: p.labelKey, amount: p.amount }] : [],
  );
}

/**
 * Grand-total difference of the selected scenario against the default one.
 * Null (badge hidden) when the selection IS the default or a side lacks a
 * calculation.
 */
function grandTotalDelta(
  scenario: TaxScenario | null,
  defaultScenario: TaxScenario | null,
): number | null {
  if (!scenario || scenario.isDefault) return null;
  if (!scenario.calculation || !defaultScenario?.calculation) return null;
  return scenario.calculation.grandTotal - defaultScenario.calculation.grandTotal;
}

// Module scope: recharts re-renders formatter results, nested components would remount.
const renderLegendText = (value: string) => (
  <span style={{ color: 'hsl(var(--foreground))', fontSize: 12 }}>{value}</span>
);

interface TaxResultSectionProps {
  /** The year's own calculation — shown when no scenario is selected. */
  result: TaxResultResponse | null;
  /** The year's own inputs (deduction details for the fallback view). */
  inputs: TaxYearInputs | null;
  /** Selected scenario; its stored calculation replaces the year's result. */
  scenario?: TaxScenario | null;
  /** The year's default scenario — baseline for the delta badge. */
  defaultScenario?: TaxScenario | null;
  /** Header slot for scenario actions (set default / delete). */
  scenarioActions?: ReactNode;
  className?: string;
}

export function TaxResultSection({
  result,
  inputs,
  scenario = null,
  defaultScenario = null,
  scenarioActions,
  className,
}: Readonly<TaxResultSectionProps>) {
  const { t } = useTranslation();
  // Controlled so the PDF export can force the deductions open before printing.
  const [deductionsOpen, setDeductionsOpen] = useState(false);

  const exportPdf = () => {
    setDeductionsOpen(true);
    requestAnimationFrame(() => globalThis.print());
  };

  const displayedResult = scenario ? scenario.calculation : result;
  const displayedInputs = scenario ? scenario.inputs : inputs;

  const titleBase = t('tax.breakdownTitle', {
    canton: displayedResult?.cantonCode ?? scenario?.inputs.cantonCode ?? '',
    year: displayedResult?.taxYear ?? scenario?.taxYear ?? '',
  });
  const title = scenario
    ? `${titleBase} · ${scenario.name}${scenario.isDefault ? ' ★' : ''}`
    : titleBase;

  // Scenario saved with insufficient inputs: show a hint instead of numbers.
  if (!displayedResult) {
    if (!scenario) return null;
    return (
      <Card className={className}>
        <CardHeader className="flex flex-row items-center justify-between gap-2 space-y-0">
          <CardTitle className="text-base">{title}</CardTitle>
          {scenarioActions}
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">{t('tax.scenarioNoCalculation')}</p>
        </CardContent>
      </Card>
    );
  }

  const deductionRows = buildDeductionRows(displayedInputs);
  const delta = grandTotalDelta(scenario, defaultScenario);

  // Design palette: Kanton violet, Gemeinde indigo, Bund green, Kirche amber.
  const pieData = [
    { name: t('tax.cantonalTax'), value: displayedResult.cantonalTax, colour: '#8b5cf6' },
    { name: t('tax.communalTax'), value: displayedResult.communalTax, colour: '#6366f1' },
    { name: t('tax.federalTax'), value: displayedResult.federalTax, colour: '#10b981' },
    { name: t('tax.churchTax'), value: displayedResult.churchTax ?? 0, colour: '#f59e0b' },
  ].filter((d) => d.value > 0);

  return (
    <div className={cn('grid gap-4 md:grid-cols-2 print:grid-cols-1', className)}>
      {/* Breakdown card */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between gap-2 space-y-0">
          <CardTitle className="text-base">{title}</CardTitle>
          <div className="flex shrink-0 items-center gap-1">
            {scenarioActions}
            {/* Icon-only on mobile so the header fits next to the long breakdown title. */}
            <Button
              variant="outline"
              size="sm"
              className="print:hidden"
              aria-label={t('tax.exportPdf')}
              onClick={exportPdf}
            >
              <Printer className="h-4 w-4 sm:mr-2" />
              <span className="hidden sm:inline">{t('tax.exportPdf')}</span>
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <Row label={t('tax.grossIncomeTotal')} value={formatCHF(displayedResult.grossIncome)} />

          <Collapsible open={deductionsOpen} onOpenChange={setDeductionsOpen}>
            <CollapsibleTrigger className="flex w-full items-center justify-between gap-2">
              <span className="flex items-center gap-1 text-muted-foreground">
                <ChevronRight
                  className={cn('h-4 w-4 transition-transform', deductionsOpen && 'rotate-90')}
                />
                {t('tax.deductionsTotal')}
              </span>
              <span className="tabular-nums text-muted-foreground">
                − {formatCHF(displayedResult.totalDeductions)}
              </span>
            </CollapsibleTrigger>
            <CollapsibleContent className="mt-2 space-y-2 pl-5">
              {deductionRows.map((row) => (
                <Row key={row.labelKey} label={t(row.labelKey)} value={formatCHF(row.amount)} muted />
              ))}
            </CollapsibleContent>
          </Collapsible>

          <div className="flex justify-between rounded-md bg-indigo-500/10 px-3 py-2 font-semibold">
            <span>{t('tax.taxableIncome')}</span>
            <span className="tabular-nums">{formatCHF(displayedResult.taxableIncome)}</span>
          </div>

          <Row label={t('tax.federalTax')} value={formatCHF(displayedResult.federalTax)} />
          <Row label={t('tax.cantonalTax')} value={formatCHF(displayedResult.cantonalTax)} />
          <Row label={t('tax.communalTax')} value={formatCHF(displayedResult.communalTax)} />
          {displayedResult.churchTax != null && displayedResult.churchTax > 0 && (
            <Row label={t('tax.churchTax')} value={formatCHF(displayedResult.churchTax)} />
          )}
          {displayedResult.wealthTax != null && displayedResult.wealthTax > 0 && (
            <Row label={t('tax.wealthTax')} value={formatCHF(displayedResult.wealthTax)} />
          )}
          <Separator />
          <div className="flex items-center justify-between">
            <span className="font-semibold">{t('tax.grandTotal')}</span>
            <span className="flex items-center gap-2">
              <span className="font-semibold tabular-nums">
                {formatCHF(displayedResult.grandTotal)}
              </span>
              {delta != null && delta !== 0 && (
                <span
                  className={cn(
                    'rounded-full px-2 py-0.5 text-xs font-semibold tabular-nums',
                    delta > 0
                      ? 'bg-rose-600/10 text-rose-600'
                      : 'bg-emerald-500/15 text-emerald-500',
                  )}
                >
                  {delta > 0 ? '+' : '−'}
                  {formatCHF(Math.abs(delta))} {t('tax.vsDefault')}
                </span>
              )}
            </span>
          </div>
          {displayedResult.pillar3aTaxSaving != null && displayedResult.pillar3aTaxSaving > 0 && (
            <Row
              label={t('tax.pillar3Saving')}
              value={`− ${formatCHF(displayedResult.pillar3aTaxSaving)}`}
              className="text-emerald-500"
            />
          )}
        </CardContent>
      </Card>

      {/* Donut + rate tiles (screen only) */}
      <Card className="print:hidden">
        <CardHeader>
          <CardTitle className="text-base">{t('tax.distribution')}</CardTitle>
        </CardHeader>
        <CardContent>
          <ResponsiveContainer width="100%" height={260}>
            <PieChart>
              <Pie data={pieData} cx="50%" cy="50%" innerRadius={60} outerRadius={100} paddingAngle={3} dataKey="value">
                {pieData.map((d) => (
                  <Cell key={d.name} fill={d.colour} />
                ))}
              </Pie>
              <Tooltip
                formatter={(v: number) => formatCHF(v)}
                contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))' }}
              />
              <Legend formatter={renderLegendText} />
            </PieChart>
          </ResponsiveContainer>
          <div className="mt-4 grid grid-cols-2 gap-2 text-sm">
            <div className="rounded-lg bg-muted p-3 text-center">
              <p className="text-muted-foreground text-xs">{t('tax.effectiveRate')}</p>
              <p className="text-xl font-bold tabular-nums">
                {formatPercent(displayedResult.effectiveRatePercent)}
              </p>
            </div>
            <div className="rounded-lg bg-muted p-3 text-center">
              <p className="text-muted-foreground text-xs">{t('tax.marginalRate')}</p>
              <p className="text-xl font-bold tabular-nums">
                {formatPercent(displayedResult.marginalRatePercent)}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function Row({
  label,
  value,
  muted,
  bold,
  className,
}: Readonly<{
  label: string;
  value: string;
  muted?: boolean;
  bold?: boolean;
  className?: string;
}>) {
  return (
    <div className="flex justify-between">
      <span className={cn(muted && 'text-muted-foreground')}>{label}</span>
      <span className={cn('tabular-nums', bold && 'font-semibold', className)}>{value}</span>
    </div>
  );
}
