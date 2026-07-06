import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Circle, ExternalLink } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { insuranceApi } from '@/api/insurance';
import type { InsuranceOverview } from '@/api/insurance';

const SKELETON_KEYS = ['sk-1', 'sk-2', 'sk-3', 'sk-4', 'sk-5', 'sk-6'];

function formatCostRange(min?: number, max?: number): string | null {
  if (!min && !max) return null;
  if (min && max) return `CHF ${min.toLocaleString('de-CH')} – ${max.toLocaleString('de-CH')} / year`;
  if (min) return `From CHF ${min.toLocaleString('de-CH')} / year`;
  if (max) return `Up to CHF ${max.toLocaleString('de-CH')} / year`;
  return null;
}

function InsuranceCard({
  item,
  lang,
  onToggle,
  isToggling,
}: Readonly<{
  item: InsuranceOverview;
  lang: string;
  onToggle: (id: string, current: boolean) => void;
  isToggling: boolean;
}>) {
  const { t } = useTranslation();
  const name = lang === 'de' ? item.nameDe : item.nameEn;
  const description = lang === 'de' ? item.descriptionDe : item.descriptionEn;
  const costRange = formatCostRange(item.typicalCostMin, item.typicalCostMax);

  return (
    <Card className={`transition-all ${item.hasInsurance ? 'border-emerald-500/40' : ''}`}>
      <CardContent className="pt-4 pb-4">
        <div className="flex items-start gap-4">
          {/* Toggle button */}
          <button
            type="button"
            onClick={() => onToggle(item.id, item.hasInsurance)}
            disabled={isToggling}
            className={`mt-0.5 shrink-0 transition-colors ${
              item.hasInsurance ? 'text-emerald-500' : 'text-muted-foreground hover:text-foreground'
            }`}
            aria-label={t('insurance.iHaveThis')}
          >
            {item.hasInsurance ? (
              <CheckCircle2 className="h-6 w-6" />
            ) : (
              <Circle className="h-6 w-6" />
            )}
          </button>

          {/* Content */}
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2 flex-wrap mb-1">
              <span className="font-medium text-sm">{name}</span>
              {item.mandatory && (
                <Badge variant="destructive" className="text-xs">{t('insurance.mandatory')}</Badge>
              )}
              {item.recommended && !item.mandatory && (
                <Badge className="text-xs bg-amber-500 hover:bg-amber-500">{t('insurance.recommended')}</Badge>
              )}
            </div>
            <p className="text-xs text-muted-foreground line-clamp-2">{description}</p>
            {costRange && (
              <p className="text-xs text-muted-foreground mt-1">
                {t('insurance.typicalCost')}: {costRange}
              </p>
            )}
          </div>

          {/* Compare link */}
          {item.comparisonUrl && (
            <a
              href={item.comparisonUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="shrink-0 text-xs text-primary flex items-center gap-1 hover:underline"
            >
              {t('insurance.compare')} <ExternalLink className="h-3 w-3" />
            </a>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

function SectionHeader({ title, count }: Readonly<{ title: string; count: number }>) {
  return (
    <div className="flex items-center gap-2 mb-3">
      <h2 className="text-base font-semibold">{title}</h2>
      <Badge variant="secondary">{count}</Badge>
    </div>
  );
}

export function Insurance() {
  const { t, i18n } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const { data: items, isLoading } = useQuery({
    queryKey: ['insurance'],
    queryFn: () => insuranceApi.getOverview(token),
    enabled: !!token,
  });

  const { mutate: toggle } = useMutation({
    mutationFn: ({ id, hasInsurance }: { id: string; hasInsurance: boolean }) =>
      insuranceApi.updateStatus(token, id, hasInsurance),
    onMutate: async ({ id, hasInsurance }) => {
      setTogglingId(id);
      // Optimistic update
      await queryClient.cancelQueries({ queryKey: ['insurance'] });
      const prev = queryClient.getQueryData<InsuranceOverview[]>(['insurance']);
      queryClient.setQueryData<InsuranceOverview[]>(['insurance'], (old) =>
        old?.map((item) => (item.id === id ? { ...item, hasInsurance } : item)),
      );
      return { prev };
    },
    onError: (_err, _vars, ctx) => {
      queryClient.setQueryData(['insurance'], ctx?.prev);
    },
    onSettled: () => {
      setTogglingId(null);
      queryClient.invalidateQueries({ queryKey: ['insurance'] });
    },
  });

  const handleToggle = (id: string, current: boolean) => {
    toggle({ id, hasInsurance: !current });
  };

  const mandatory = (items ?? []).filter((i) => i.mandatory);
  const recommended = (items ?? []).filter((i) => !i.mandatory && i.recommended);
  const optional = (items ?? []).filter((i) => !i.mandatory && !i.recommended);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">{t('insurance.title')}</h1>

      {isLoading ? (
        <div className="space-y-3">
          {SKELETON_KEYS.map((key) => <Skeleton key={key} className="h-20 w-full" />)}
        </div>
      ) : (
        <>
          {mandatory.length > 0 && (
            <section>
              <SectionHeader title={t('insurance.mandatory')} count={mandatory.length} />
              <div className="space-y-2">
                {mandatory.map((item) => (
                  <InsuranceCard
                    key={item.id}
                    item={item}
                    lang={i18n.language}
                    onToggle={handleToggle}
                    isToggling={togglingId === item.id}
                  />
                ))}
              </div>
            </section>
          )}

          {recommended.length > 0 && (
            <section>
              <SectionHeader title={t('insurance.recommended')} count={recommended.length} />
              <div className="space-y-2">
                {recommended.map((item) => (
                  <InsuranceCard
                    key={item.id}
                    item={item}
                    lang={i18n.language}
                    onToggle={handleToggle}
                    isToggling={togglingId === item.id}
                  />
                ))}
              </div>
            </section>
          )}

          {optional.length > 0 && (
            <section>
              <SectionHeader title={t('insurance.optional')} count={optional.length} />
              <div className="space-y-2">
                {optional.map((item) => (
                  <InsuranceCard
                    key={item.id}
                    item={item}
                    lang={i18n.language}
                    onToggle={handleToggle}
                    isToggling={togglingId === item.id}
                  />
                ))}
              </div>
            </section>
          )}

          {(items ?? []).length === 0 && (
            <div className="py-16 text-center text-muted-foreground">No insurance types configured.</div>
          )}
        </>
      )}
    </div>
  );
}
