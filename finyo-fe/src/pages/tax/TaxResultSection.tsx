import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { ChevronRight, Printer } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import {
  Collapsible, CollapsibleContent, CollapsibleTrigger,
} from '@/components/ui/collapsible';
import type { TaxResultResponse, TaxYearInputs } from '@/api/tax';
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

// Module scope: recharts re-renders formatter results, nested components would remount.
const renderLegendText = (value: string) => (
  <span style={{ color: 'hsl(var(--foreground))', fontSize: 12 }}>{value}</span>
);

interface TaxResultSectionProps {
  result: TaxResultResponse;
  inputs: TaxYearInputs | null;
  className?: string;
}

export function TaxResultSection({ result, inputs, className }: Readonly<TaxResultSectionProps>) {
  const { t } = useTranslation();
  // Controlled so the PDF export can force the deductions open before printing.
  const [deductionsOpen, setDeductionsOpen] = useState(false);

  const exportPdf = () => {
    setDeductionsOpen(true);
    requestAnimationFrame(() => globalThis.print());
  };

  const deductionRows = buildDeductionRows(inputs);

  const pieData = [
    { name: t('tax.federalTax'), value: result.federalTax, colour: '#6366f1' },
    { name: t('tax.cantonalTax'), value: result.cantonalTax, colour: '#8b5cf6' },
    { name: t('tax.communalTax'), value: result.communalTax, colour: '#10b981' },
    { name: t('tax.churchTax'), value: result.churchTax ?? 0, colour: '#f59e0b' },
  ].filter((d) => d.value > 0);

  return (
    <div className={cn('grid gap-4 md:grid-cols-2 print:grid-cols-1', className)}>
      {/* Breakdown card */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between space-y-0">
          <CardTitle className="text-base">
            {t('tax.breakdownTitle', { canton: result.cantonCode, year: result.taxYear })}
          </CardTitle>
          <Button variant="outline" size="sm" className="print:hidden" onClick={exportPdf}>
            <Printer className="mr-2 h-4 w-4" />
            {t('tax.exportPdf')}
          </Button>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <Row label={t('tax.grossIncomeTotal')} value={formatCHF(result.grossIncome)} />

          <Collapsible open={deductionsOpen} onOpenChange={setDeductionsOpen}>
            <CollapsibleTrigger className="flex w-full items-center justify-between gap-2">
              <span className="flex items-center gap-1 text-muted-foreground">
                <ChevronRight
                  className={cn('h-4 w-4 transition-transform', deductionsOpen && 'rotate-90')}
                />
                {t('tax.deductionsTotal')}
              </span>
              <span className="text-muted-foreground">− {formatCHF(result.totalDeductions)}</span>
            </CollapsibleTrigger>
            <CollapsibleContent className="mt-2 space-y-2 pl-5">
              {deductionRows.map((row) => (
                <Row key={row.labelKey} label={t(row.labelKey)} value={formatCHF(row.amount)} muted />
              ))}
            </CollapsibleContent>
          </Collapsible>

          <div className="flex justify-between rounded-md bg-indigo-500/10 px-3 py-2 font-semibold">
            <span>{t('tax.taxableIncome')}</span>
            <span>{formatCHF(result.taxableIncome)}</span>
          </div>

          <Row label={t('tax.federalTax')} value={formatCHF(result.federalTax)} />
          <Row label={t('tax.cantonalTax')} value={formatCHF(result.cantonalTax)} />
          <Row label={t('tax.communalTax')} value={formatCHF(result.communalTax)} />
          {result.churchTax != null && result.churchTax > 0 && (
            <Row label={t('tax.churchTax')} value={formatCHF(result.churchTax)} />
          )}
          {result.wealthTax != null && result.wealthTax > 0 && (
            <Row label={t('tax.wealthTax')} value={formatCHF(result.wealthTax)} />
          )}
          <Separator />
          <Row label={t('tax.grandTotal')} value={formatCHF(result.grandTotal)} bold />
          {result.pillar3aTaxSaving != null && result.pillar3aTaxSaving > 0 && (
            <Row
              label={t('tax.pillar3Saving')}
              value={`− ${formatCHF(result.pillar3aTaxSaving)}`}
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
              <p className="text-xl font-bold">{formatPercent(result.effectiveRatePercent)}</p>
            </div>
            <div className="rounded-lg bg-muted p-3 text-center">
              <p className="text-muted-foreground text-xs">{t('tax.marginalRate')}</p>
              <p className="text-xl font-bold">{formatPercent(result.marginalRatePercent)}</p>
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
      <span className={cn(bold && 'font-semibold', className)}>{value}</span>
    </div>
  );
}
