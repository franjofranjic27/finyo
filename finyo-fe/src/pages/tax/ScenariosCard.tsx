import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Star, Trash2 } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { useAuth } from '@/auth/useAuth';
import { taxApi } from '@/api/tax';
import type { TaxScenario, TaxYearInputs } from '@/api/tax';
import { formatCHF, formatDate } from '@/lib/formatters';

interface ScenariosCardProps {
  year: number;
  inputs: TaxYearInputs | null;
  className?: string;
}

/** Saved input snapshots ("scenarios") for a tax year, with one optional default. */
export function ScenariosCard({ year, inputs, className }: Readonly<ScenariosCardProps>) {
  const { t, i18n } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [name, setName] = useState('');
  const [saveAsDefault, setSaveAsDefault] = useState(false);

  const { data: scenarios, error: scenariosError } = useQuery({
    queryKey: ['tax', 'scenarios', String(year)],
    queryFn: () => taxApi.getScenarios(token, year),
    enabled: !!token,
  });

  const invalidateScenarios = () => {
    queryClient.invalidateQueries({ queryKey: ['tax', 'scenarios', String(year)] });
  };

  const createScenario = useMutation({
    mutationFn: (currentInputs: TaxYearInputs) =>
      taxApi.createScenario(token, year, {
        ...currentInputs,
        name: name.trim(),
        isDefault: saveAsDefault,
      }),
    onSuccess: () => {
      invalidateScenarios();
      setDialogOpen(false);
      setName('');
      setSaveAsDefault(false);
    },
  });

  const setDefault = useMutation({
    mutationFn: (scenarioId: string) => taxApi.setDefaultScenario(token, year, scenarioId),
    onSuccess: invalidateScenarios,
  });

  const deleteScenario = useMutation({
    mutationFn: (scenarioId: string) => taxApi.deleteScenario(token, year, scenarioId),
    onSuccess: invalidateScenarios,
  });

  const saveScenario = () => {
    if (inputs) createScenario.mutate(inputs);
  };

  const confirmDeleteScenario = (scenario: TaxScenario) => {
    if (globalThis.confirm(t('tax.confirmDeleteScenario', { name: scenario.name }))) {
      deleteScenario.mutate(scenario.id);
    }
  };

  const listError = scenariosError ?? setDefault.error ?? deleteScenario.error;

  return (
    <Card className={className}>
      <CardHeader className="flex flex-row items-center justify-between space-y-0">
        <CardTitle className="text-base">{t('tax.scenarios')}</CardTitle>
        <Button
          variant="outline"
          size="sm"
          onClick={() => setDialogOpen(true)}
          disabled={!inputs}
        >
          <Plus className="mr-2 h-4 w-4" />
          {t('tax.saveScenario')}
        </Button>
      </CardHeader>
      <CardContent className="space-y-3">
        {!scenariosError && (scenarios ?? []).length === 0 && (
          <p className="text-sm text-muted-foreground">{t('tax.noScenarios')}</p>
        )}
        {(scenarios ?? []).map((scenario) => (
          <div key={scenario.id} className="flex items-center gap-3 text-sm">
            <div className="min-w-0 flex-1">
              <p className="flex items-center gap-2">
                <span className="truncate">{scenario.name}</span>
                {scenario.isDefault && (
                  <Badge variant="secondary">{t('tax.defaultScenario')}</Badge>
                )}
              </p>
              <p className="text-xs text-muted-foreground">
                {formatDate(scenario.createdAt, i18n.language)}
                {scenario.calculation &&
                  ` · ${t('tax.totalIncomeTax')} ${formatCHF(scenario.calculation.totalIncomeTax)}` +
                    ` · ${t('tax.grandTotal')} ${formatCHF(scenario.calculation.grandTotal)}`}
              </p>
            </div>
            {!scenario.isDefault && (
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 text-muted-foreground"
                aria-label={t('tax.setAsDefault')}
                disabled={setDefault.isPending}
                onClick={() => setDefault.mutate(scenario.id)}
              >
                <Star className="h-4 w-4" />
              </Button>
            )}
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 text-muted-foreground hover:text-destructive"
              aria-label={t('tax.deleteScenario')}
              disabled={deleteScenario.isPending}
              onClick={() => confirmDeleteScenario(scenario)}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        ))}
        {listError && <p className="text-sm text-destructive">{listError.message}</p>}
      </CardContent>

      {/* Save scenario dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t('tax.saveScenario')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="scenario-name">{t('tax.scenarioName')}</Label>
              <Input
                id="scenario-name"
                value={name}
                maxLength={100}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <div className="flex items-center gap-2">
              <input
                id="scenario-default"
                type="checkbox"
                className="h-4 w-4 accent-primary"
                checked={saveAsDefault}
                onChange={(e) => setSaveAsDefault(e.target.checked)}
              />
              <Label htmlFor="scenario-default">{t('tax.saveAsDefault')}</Label>
            </div>
            {createScenario.error && (
              <p className="text-sm text-destructive">{createScenario.error.message}</p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button
              onClick={saveScenario}
              disabled={!name.trim() || createScenario.isPending}
            >
              {createScenario.isPending ? t('common.loading') : t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
