import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { SalaryInputMode } from '@/api/salary';
import { formatCHF } from '@/lib/formatters';

export interface IncomeValues {
  inputMode: SalaryInputMode;
  /** Gross amount as entered — monthly or yearly depending on inputMode. */
  amount: string;
  thirteenthSalary: boolean;
}

interface StepIncomeProps {
  values: IncomeValues;
  onChange: (patch: Partial<IncomeValues>) => void;
}

export function StepIncome({ values, onChange }: Readonly<StepIncomeProps>) {
  const { t } = useTranslation();

  const amount = Number.parseFloat(values.amount) || 0;
  const monthsPerYear = values.thirteenthSalary ? 13 : 12;
  const isYearly = values.inputMode === 'YEARLY';

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label>{t('budget.salary.inputs.mode')}</Label>
        <div className="flex gap-1">
          <Button
            variant={values.inputMode === 'MONTHLY' ? 'default' : 'outline'}
            size="sm"
            onClick={() => onChange({ inputMode: 'MONTHLY' })}
          >
            {t('budget.salary.inputs.modeMonthly')}
          </Button>
          <Button
            variant={values.inputMode === 'YEARLY' ? 'default' : 'outline'}
            size="sm"
            onClick={() => onChange({ inputMode: 'YEARLY' })}
          >
            {t('budget.salary.inputs.modeYearly')}
          </Button>
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="onboarding-gross">
          {t(isYearly ? 'budget.salary.inputs.grossYearlyLabel' : 'budget.salary.inputs.grossMonthly')}
        </Label>
        <Input
          id="onboarding-gross"
          type="number"
          min={0}
          step={0.01}
          value={values.amount}
          onChange={(e) => onChange({ amount: e.target.value })}
        />
        {amount > 0 && (
          <p className="text-xs text-muted-foreground">
            {isYearly
              ? t('budget.salary.inputs.monthlyDerived', {
                  amount: formatCHF(amount / monthsPerYear),
                })
              : t('budget.salary.inputs.grossYearly', {
                  amount: formatCHF(amount * monthsPerYear),
                })}
          </p>
        )}
      </div>

      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <Checkbox
            id="onboarding-thirteenth"
            checked={values.thirteenthSalary}
            onCheckedChange={(checked) => onChange({ thirteenthSalary: checked === true })}
          />
          <Label htmlFor="onboarding-thirteenth">{t('budget.salary.inputs.thirteenth')}</Label>
        </div>
        {isYearly && values.thirteenthSalary && (
          <p className="text-xs text-muted-foreground">
            {t('budget.salary.inputs.yearlyIncludesThirteenth')}
          </p>
        )}
      </div>

      <p className="text-xs text-muted-foreground">{t('onboarding.income.hint')}</p>
    </div>
  );
}
