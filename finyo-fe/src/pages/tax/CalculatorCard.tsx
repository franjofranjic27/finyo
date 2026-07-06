import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Calculator } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { useAuth } from '@/auth/useAuth';
import { taxApi } from '@/api/tax';
import type { ChurchAffiliation, TaxCivilStatus, TaxCommune, TaxYearInputs } from '@/api/tax';

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
  className?: string;
}

export function CalculatorCard({ year, inputs, className }: Readonly<CalculatorCardProps>) {
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

  const { data: communes } = useQuery({
    queryKey: ['tax', 'communes', canton, String(year)],
    queryFn: () => taxApi.getCommunes(token, canton, year),
    enabled: !!token && !!canton,
  });

  const selectedCommune = communes?.find((c) => String(c.bfsNumber) === bfsNumber) ?? null;
  const churchMultiplier = resolveChurchMultiplier(selectedCommune, church);

  const { mutate, isPending, error } = useMutation({
    mutationFn: () => {
      const payload: TaxYearInputs = {
        ...(inputs ?? EMPTY_INPUTS),
        cantonCode: canton,
        bfsNumber: bfsNumber ? Number(bfsNumber) : null,
        civilStatus,
        churchAffiliation: church,
        numberOfChildren: Number.parseInt(children) || 0,
        grossEmploymentIncome: Number.parseFloat(grossIncome) || 0,
        investmentIncome: investmentIncome ? Number.parseFloat(investmentIncome) : null,
        pillar3aContribution: pillar3a ? Number.parseFloat(pillar3a) : null,
        netWealth: netWealth ? Number.parseFloat(netWealth) : null,
      };
      return taxApi.upsertYear(token, year, payload);
    },
    onSuccess: (detail) => {
      queryClient.setQueryData(['tax', 'year', String(year)], detail);
      queryClient.invalidateQueries({ queryKey: ['tax', 'years'] });
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
            <Input type="number" value={grossIncome} onChange={(e) => setGrossIncome(e.target.value)} placeholder="100000" />
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
            <Input type="number" value={pillar3a} onChange={(e) => setPillar3a(e.target.value)} max={7258} />
          </div>
          <div className="space-y-2">
            <Label>{t('tax.netWealth')} (CHF)</Label>
            <Input type="number" value={netWealth} onChange={(e) => setNetWealth(e.target.value)} />
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
          <Button onClick={() => mutate()} disabled={!grossIncome || isPending}>
            <Calculator className="mr-2 h-4 w-4" />
            {isPending ? t('common.loading') : t('tax.calculateForYear', { year })}
          </Button>
          {error && <p className="text-sm text-destructive">{error.message}</p>}
        </div>
      </CardContent>
    </Card>
  );
}
