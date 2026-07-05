import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation } from '@tanstack/react-query';
import {
  AreaChart, Area, PieChart, Pie, Cell,
  Tooltip, ResponsiveContainer, XAxis, YAxis, CartesianGrid, Legend,
} from 'recharts';
import { Calculator } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { useAuth } from '@/auth/useAuth';
import { taxApi } from '@/api/tax';
import type { TaxResultResponse, Pillar3Result, TaxCivilStatus } from '@/api/tax';
import { formatCHF, formatPercent } from '@/lib/formatters';

const CANTONS = [
  'AG','AI','AR','BE','BL','BS','FR','GE','GL','GR',
  'JU','LU','NE','NW','OW','SG','SH','SO','SZ','TG',
  'TI','UR','VD','VS','ZG','ZH',
];

const CIVIL_STATUS_OPTIONS: { value: TaxCivilStatus; label: string }[] = [
  { value: 'SINGLE', label: 'Single' },
  { value: 'MARRIED', label: 'Married' },
  { value: 'WIDOWED', label: 'Widowed' },
];

const PIE_COLOURS = ['#6366f1', '#8b5cf6', '#10b981'];

// --- Income Tax Tab ---

function IncomeTaxForm() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const currentYear = new Date().getFullYear();
  const [taxYear, setTaxYear] = useState(String(currentYear));
  const [canton, setCanton] = useState('SG');
  const [civilStatus, setCivilStatus] = useState<TaxCivilStatus>('SINGLE');
  const [children, setChildren] = useState('0');
  const [grossIncome, setGrossIncome] = useState('');
  const [investmentIncome, setInvestmentIncome] = useState('');
  const [pillar3a, setPillar3a] = useState('');
  const [netWealth, setNetWealth] = useState('');

  const { mutate, data: result, isPending } = useMutation({
    mutationFn: () =>
      taxApi.calculate(token, {
        taxYear: parseInt(taxYear),
        cantonCode: canton,
        civilStatus,
        numberOfChildren: parseInt(children) || 0,
        grossEmploymentIncome: parseFloat(grossIncome) || 0,
        investmentIncome: investmentIncome ? parseFloat(investmentIncome) : null,
        pillar3aContribution: pillar3a ? parseFloat(pillar3a) : null,
        netWealth: netWealth ? parseFloat(netWealth) : null,
      }),
  });

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('tax.title')}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
            <div className="space-y-2">
              <Label>{t('tax.taxYear')}</Label>
              <Input type="number" value={taxYear} onChange={(e) => setTaxYear(e.target.value)} min={2020} max={2030} />
            </div>
            <div className="space-y-2">
              <Label>{t('tax.canton')}</Label>
              <Select value={canton} onValueChange={setCanton}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {CANTONS.map((c) => (
                    <SelectItem key={c} value={c}>{c}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t('tax.civilStatus')}</Label>
              <Select value={civilStatus} onValueChange={(v) => setCivilStatus(v as TaxCivilStatus)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {CIVIL_STATUS_OPTIONS.map((o) => (
                    <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t('tax.children')}</Label>
              <Input type="number" value={children} onChange={(e) => setChildren(e.target.value)} min={0} max={20} />
            </div>
            <div className="space-y-2">
              <Label>{t('tax.grossIncome')} (CHF) *</Label>
              <Input type="number" value={grossIncome} onChange={(e) => setGrossIncome(e.target.value)} placeholder="100000" />
            </div>
            <div className="space-y-2">
              <Label>{t('tax.investmentIncome')} (CHF)</Label>
              <Input type="number" value={investmentIncome} onChange={(e) => setInvestmentIncome(e.target.value)} placeholder="Optional" />
            </div>
            <div className="space-y-2">
              <Label>{t('tax.pillar3')} (CHF, max 7'258)</Label>
              <Input type="number" value={pillar3a} onChange={(e) => setPillar3a(e.target.value)} placeholder="Optional" max={7258} />
            </div>
            <div className="space-y-2">
              <Label>Net Wealth (CHF)</Label>
              <Input type="number" value={netWealth} onChange={(e) => setNetWealth(e.target.value)} placeholder="Optional" />
            </div>
          </div>
          <div className="mt-4">
            <Button onClick={() => mutate()} disabled={!grossIncome || isPending}>
              <Calculator className="mr-2 h-4 w-4" />
              {isPending ? t('common.loading') : t('tax.calculate')}
            </Button>
          </div>
        </CardContent>
      </Card>

      {isPending && <TaxResultSkeleton />}
      {result && <TaxResult result={result} />}
    </div>
  );
}

function TaxResultSkeleton() {
  return (
    <div className="grid gap-4 md:grid-cols-2">
      <Skeleton className="h-64 w-full" />
      <Skeleton className="h-64 w-full" />
    </div>
  );
}

function TaxResult({ result }: { result: TaxResultResponse }) {
  const { t } = useTranslation();

  const pieData = [
    { name: t('tax.federalTax'), value: result.federalTax },
    { name: t('tax.cantonalTax'), value: result.cantonalTax },
    { name: t('tax.communalTax'), value: result.communalTax },
  ].filter((d) => d.value > 0);

  return (
    <div className="grid gap-4 md:grid-cols-2">
      {/* Breakdown card */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Tax Breakdown — Canton {result.cantonCode} {result.taxYear}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <Row label="Gross Income" value={formatCHF(result.grossIncome)} />
          <Row label="Total Deductions" value={`− ${formatCHF(result.totalDeductions)}`} muted />
          <Row label="Taxable Income" value={formatCHF(result.taxableIncome)} bold />
          <Separator />
          <Row label={t('tax.federalTax')} value={formatCHF(result.federalTax)} />
          <Row label={t('tax.cantonalTax')} value={formatCHF(result.cantonalTax)} />
          <Row label={t('tax.communalTax')} value={formatCHF(result.communalTax)} />
          <Separator />
          <Row label="Total Income Tax" value={formatCHF(result.totalIncomeTax)} bold />
          {result.wealthTax != null && result.wealthTax > 0 && (
            <Row label="Wealth Tax" value={formatCHF(result.wealthTax)} />
          )}
          <Row label="Grand Total" value={formatCHF(result.grandTotal)} bold />
          <Separator />
          <Row label={t('tax.effectiveRate')} value={formatPercent(result.effectiveRatePercent)} />
          <Row label={t('tax.marginalRate')} value={formatPercent(result.marginalRatePercent)} />
          {result.pillar3aTaxSaving != null && result.pillar3aTaxSaving > 0 && (
            <>
              <Separator />
              <Row
                label="Pillar 3a Tax Saving"
                value={`− ${formatCHF(result.pillar3aTaxSaving)}`}
                className="text-emerald-500"
              />
            </>
          )}
        </CardContent>
      </Card>

      {/* Pie chart */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Tax Distribution</CardTitle>
        </CardHeader>
        <CardContent>
          <ResponsiveContainer width="100%" height={260}>
            <PieChart>
              <Pie data={pieData} cx="50%" cy="50%" innerRadius={60} outerRadius={100} paddingAngle={3} dataKey="value">
                {pieData.map((_, i) => (
                  <Cell key={i} fill={PIE_COLOURS[i % PIE_COLOURS.length]} />
                ))}
              </Pie>
              <Tooltip
                formatter={(v: number) => formatCHF(v)}
                contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))' }}
              />
              <Legend formatter={(v) => <span style={{ color: 'hsl(var(--foreground))', fontSize: 12 }}>{v}</span>} />
            </PieChart>
          </ResponsiveContainer>
          <div className="mt-4 grid grid-cols-2 gap-2 text-sm">
            <div className="rounded-lg bg-muted p-3 text-center">
              <p className="text-muted-foreground text-xs">Effective Rate</p>
              <p className="text-xl font-bold">{formatPercent(result.effectiveRatePercent)}</p>
            </div>
            <div className="rounded-lg bg-muted p-3 text-center">
              <p className="text-muted-foreground text-xs">Marginal Rate</p>
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
}: {
  label: string;
  value: string;
  muted?: boolean;
  bold?: boolean;
  className?: string;
}) {
  return (
    <div className="flex justify-between">
      <span className={muted ? 'text-muted-foreground' : ''}>{label}</span>
      <span className={`${bold ? 'font-semibold' : ''} ${className ?? ''}`}>{value}</span>
    </div>
  );
}

// --- Pillar 3a Tab ---

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
        currentBalance: parseFloat(balance) || 0,
        annualContribution: parseFloat(contribution) || 0,
        assumedAnnualReturnPercent: parseFloat(returnPct) || 5,
        yearsToRetirement: parseInt(years) || 30,
        grossEmploymentIncome: income ? parseFloat(income) : null,
        civilStatus: income ? civilStatus : null,
        cantonCode: income ? canton : null,
        taxYear: currentYear,
      }),
  });

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('tax.pillar3Title')}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
            <div className="space-y-2">
              <Label>{t('tax.currentBalance')} (CHF)</Label>
              <Input type="number" value={balance} onChange={(e) => setBalance(e.target.value)} placeholder="0" />
            </div>
            <div className="space-y-2">
              <Label>{t('tax.contribution')} (CHF, max 7'258)</Label>
              <Input type="number" value={contribution} onChange={(e) => setContribution(e.target.value)} max={7258} />
            </div>
            <div className="space-y-2">
              <Label>{t('tax.returnRate')}</Label>
              <Input type="number" value={returnPct} onChange={(e) => setReturnPct(e.target.value)} step={0.1} min={0} max={20} />
            </div>
            <div className="space-y-2">
              <Label>{t('tax.yearsToRetirement')}</Label>
              <Input type="number" value={years} onChange={(e) => setYears(e.target.value)} min={1} max={50} />
            </div>
            <div className="space-y-2 col-span-2 md:col-span-1">
              <Label>Gross Income (CHF, for tax saving)</Label>
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
                  <Label>{t('tax.civilStatus')}</Label>
                  <Select value={civilStatus} onValueChange={(v) => setCivilStatus(v as TaxCivilStatus)}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {CIVIL_STATUS_OPTIONS.map((o) => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}
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
      {result && <Pillar3Result result={result} years={parseInt(years)} />}
    </div>
  );
}

function Pillar3Result({ result, years }: { result: Pillar3Result; years: number }) {
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
        <MetricCard label={t('tax.projectedBalance')} value={formatCHF(result.projectedBalanceAtRetirement)} />
        {result.annualTaxSaving > 0 && (
          <MetricCard label={t('tax.taxSaving') + ' / year'} value={formatCHF(result.annualTaxSaving)} highlight />
        )}
        <MetricCard label={t('tax.payoutTax')} value={formatCHF(result.estimatedPayoutTax)} />
        <MetricCard label={t('tax.netValue')} value={formatCHF(result.netValueAfterPayoutTax)} />
      </div>

      {/* Contribution cap info */}
      {!result.isMaxContribution && (
        <Card className="border-amber-500/30 bg-amber-500/5">
          <CardContent className="pt-4 text-sm text-amber-600 dark:text-amber-400">
            You could contribute{' '}
            <strong>{formatCHF(result.remainingUntilMax)}</strong> more to reach the
            maximum of <strong>{formatCHF(result.maxContribution)}</strong>, saving an
            additional <strong>{formatCHF(result.taxSavingIfMaxContribution - result.annualTaxSaving)}</strong> in taxes.
          </CardContent>
        </Card>
      )}

      {/* Growth chart */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Balance Growth over {years} Years</CardTitle>
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
              <Legend formatter={(v) => <span style={{ color: 'hsl(var(--foreground))', fontSize: 12 }}>{v}</span>} />
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
            <p className="font-medium">Compared to taxable savings account</p>
            <p className="text-muted-foreground">
              Same gross contributions in a regular taxable account would yield approximately{' '}
              <strong>{formatCHF(result.equivalentTaxableSavings)}</strong> — the 3a account yields{' '}
              <strong className="text-emerald-500">
                +{formatCHF(result.netValueAfterPayoutTax - result.equivalentTaxableSavings)}
              </strong>{' '}
              more after payout tax.
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function MetricCard({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <Card className={highlight ? 'border-emerald-500/30 bg-emerald-500/5' : ''}>
      <CardContent className="pt-4">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className={`text-xl font-bold mt-1 ${highlight ? 'text-emerald-500' : ''}`}>{value}</p>
      </CardContent>
    </Card>
  );
}

// --- Main page ---

export function Tax() {
  const { t } = useTranslation();

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">{t('tax.title')}</h1>
      <Tabs defaultValue="income">
        <TabsList>
          <TabsTrigger value="income">Income Tax</TabsTrigger>
          <TabsTrigger value="pillar3">{t('tax.pillar3Title')}</TabsTrigger>
        </TabsList>
        <TabsContent value="income" className="mt-4">
          <IncomeTaxForm />
        </TabsContent>
        <TabsContent value="pillar3" className="mt-4">
          <Pillar3Form />
        </TabsContent>
      </Tabs>
    </div>
  );
}
