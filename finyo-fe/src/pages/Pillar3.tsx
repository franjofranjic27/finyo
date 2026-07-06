import { useState } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { useMutation } from '@tanstack/react-query';
import {
  AreaChart, Area, Tooltip, ResponsiveContainer, XAxis, YAxis, CartesianGrid, Legend,
} from 'recharts';
import { Calculator } from 'lucide-react';
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

// Module scope: recharts re-renders formatter results, nested components would remount.
const renderLegendText = (value: string) => (
  <span style={{ color: 'hsl(var(--foreground))', fontSize: 12 }}>{value}</span>
);

function Pillar3Form() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const currentYear = new Date().getFullYear();
  const [balance, setBalance] = useState('');
  const [contribution, setContribution] = useState('7258');
  const [returnPct, setReturnPct] = useState('5.0');
  const [years, setYears] = useState('30');
  const [income, setIncome] = useState('');
  const [canton, setCanton] = useState('SG');
  const [civilStatus, setCivilStatus] = useState<TaxCivilStatus>('SINGLE');

  const { mutate, data: result, isPending } = useMutation({
    mutationFn: () =>
      taxApi.calculatePillar3(token, {
        currentBalance: Number.parseFloat(balance) || 0,
        annualContribution: Number.parseFloat(contribution) || 0,
        assumedAnnualReturnPercent: Number.parseFloat(returnPct) || 5,
        yearsToRetirement: Number.parseInt(years) || 30,
        grossEmploymentIncome: income ? Number.parseFloat(income) : null,
        civilStatus: income ? civilStatus : null,
        cantonCode: income ? canton : null,
        taxYear: currentYear,
      }),
  });

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
              <Input type="number" value={returnPct} onChange={(e) => setReturnPct(e.target.value)} step={0.1} min={0} max={20} />
            </div>
            <div className="space-y-2">
              <Label>{t('pillar3.yearsToRetirement')}</Label>
              <Input type="number" value={years} onChange={(e) => setYears(e.target.value)} min={1} max={50} />
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
          <div className="mt-4">
            <Button onClick={() => mutate()} disabled={isPending}>
              <Calculator className="mr-2 h-4 w-4" />
              {isPending ? t('common.loading') : t('tax.calculate')}
            </Button>
          </div>
        </CardContent>
      </Card>

      {isPending && <Skeleton className="h-96 w-full" />}
      {result && <Pillar3Result result={result} years={Number.parseInt(years)} />}
    </div>
  );
}

function Pillar3Result({ result, years }: Readonly<{ result: Pillar3Result; years: number }>) {
  const { t } = useTranslation();

  const chartData = result.yearlyProjection
    .filter((_, i) => i < 5 || (i + 1) % 5 === 0 || i === result.yearlyProjection.length - 1)
    .map((p) => ({
      year: `Y${p.year}`,
      Contributions: Math.round(p.contribution * p.year),
      Returns: Math.round(p.balance - p.contribution * p.year),
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
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis dataKey="year" tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }} axisLine={false} tickLine={false} />
              <YAxis
                tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }}
                axisLine={false}
                tickLine={false}
                tickFormatter={(v: number) => `${(v / 1000).toFixed(0)}k`}
              />
              <Tooltip
                formatter={(v: number) => formatCHF(v)}
                contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))' }}
              />
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

export function Pillar3() {
  const { t } = useTranslation();

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">{t('pillar3.title')}</h1>
      <Pillar3Form />
    </div>
  );
}
