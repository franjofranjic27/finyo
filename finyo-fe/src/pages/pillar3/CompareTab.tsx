import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { PackageSearch } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { pillar3Api } from '@/api/pillar3';
import type { Pillar3Product } from '@/api/pillar3';
import { formatCHF } from '@/lib/formatters';
import { ProductSearchCombobox } from './ProductSearchCombobox';
import { ComparisonTable } from './ComparisonTable';
import { CompareChart } from './CompareChart';
import { productColor } from './productColors';

const MAX_SELECTED_PRODUCTS = 10;
const DEBOUNCE_MS = 300;

const DEFAULT_BALANCE = '0';
const DEFAULT_CONTRIBUTION = '7258';
const DEFAULT_YEARS = '20';

function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}

export function CompareTab() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';

  const [balance, setBalance] = useState(DEFAULT_BALANCE);
  const [contribution, setContribution] = useState(DEFAULT_CONTRIBUTION);
  const [years, setYears] = useState(DEFAULT_YEARS);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const debouncedBalance = useDebouncedValue(balance, DEBOUNCE_MS);
  const debouncedContribution = useDebouncedValue(contribution, DEBOUNCE_MS);
  const debouncedYears = useDebouncedValue(years, DEBOUNCE_MS);

  const { data: catalog, isLoading: catalogLoading } = useQuery({
    queryKey: ['pillar3-products'],
    queryFn: () => pillar3Api.getProducts(token),
    enabled: !!token,
  });

  // Sorted copy for a stable query key — the backend sorts the response anyway.
  const sortedIds = useMemo(
    () => [...selectedIds].sort((a, b) => a.localeCompare(b)),
    [selectedIds],
  );
  const yearsValue = Number.parseInt(debouncedYears) || Number.parseInt(DEFAULT_YEARS);

  const {
    data: comparison,
    isLoading: comparisonLoading,
    error: comparisonError,
  } = useQuery({
    queryKey: ['pillar3-compare', debouncedBalance, debouncedContribution, debouncedYears, sortedIds],
    queryFn: () =>
      pillar3Api.compare(token, {
        currentBalance: Number.parseFloat(debouncedBalance) || 0,
        annualContribution: Number.parseFloat(debouncedContribution) || 0,
        years: yearsValue,
        productIds: sortedIds,
      }),
    enabled: !!token && sortedIds.length > 0,
    placeholderData: keepPreviousData,
  });

  const colorByProductId = useMemo(
    () => new Map(selectedIds.map((id, index) => [id, productColor(index)])),
    [selectedIds],
  );

  const addProduct = (product: Pillar3Product) => {
    setSelectedIds((prev) =>
      prev.length < MAX_SELECTED_PRODUCTS && !prev.includes(product.id)
        ? [...prev, product.id]
        : prev,
    );
  };

  const removeProduct = (productId: string) => {
    setSelectedIds((prev) => prev.filter((id) => id !== productId));
  };

  const catalogEmpty = !catalogLoading && (catalog ?? []).length === 0;
  const selectionFull = selectedIds.length >= MAX_SELECTED_PRODUCTS;

  return (
    <div className="space-y-6">
      {/* Parameters + product search */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('pillar3.compare.formTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
            <div className="space-y-2">
              <Label htmlFor="compare-balance">{t('pillar3.compare.currentBalance')} (CHF)</Label>
              <Input
                id="compare-balance"
                type="number"
                min={0}
                value={balance}
                onChange={(e) => setBalance(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="compare-contribution">
                {t('pillar3.compare.annualContribution')} (CHF)
              </Label>
              <Input
                id="compare-contribution"
                type="number"
                min={0}
                max={comparison?.maxAnnualContribution}
                value={contribution}
                onChange={(e) => setContribution(e.target.value)}
              />
              {comparison && (
                <p className="text-xs text-muted-foreground">
                  {t('pillar3.compare.capHint', {
                    max: formatCHF(comparison.maxAnnualContribution),
                  })}
                </p>
              )}
            </div>
            <div className="space-y-2 col-span-2 md:col-span-1">
              <Label htmlFor="compare-years">{t('pillar3.compare.years')}</Label>
              <Input
                id="compare-years"
                type="number"
                min={1}
                max={50}
                value={years}
                onChange={(e) => setYears(e.target.value)}
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label>{t('pillar3.compare.searchLabel')}</Label>
            <ProductSearchCombobox
              products={catalog ?? []}
              selectedIds={selectedIds}
              onSelect={addProduct}
              disabled={catalogEmpty || selectionFull}
            />
            {selectionFull && (
              <p className="text-xs text-muted-foreground">
                {t('pillar3.compare.maxProducts', { max: MAX_SELECTED_PRODUCTS })}
              </p>
            )}
          </div>
        </CardContent>
      </Card>

      {catalogLoading && <Skeleton className="h-40 w-full" />}

      {/* Empty states */}
      {catalogEmpty && (
        <EmptyState text={t('pillar3.compare.emptyCatalog')} />
      )}
      {!catalogLoading && !catalogEmpty && selectedIds.length === 0 && (
        <EmptyState text={t('pillar3.compare.emptySelection')} />
      )}

      {comparisonError && selectedIds.length > 0 && (
        <p className="text-sm text-destructive">{comparisonError.message}</p>
      )}

      {selectedIds.length > 0 && comparisonLoading && !comparison && (
        <Skeleton className="h-64 w-full" />
      )}

      {/* Comparison result */}
      {comparison && selectedIds.length > 0 && (
        <>
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t('pillar3.compare.tableTitle')}</CardTitle>
            </CardHeader>
            <CardContent>
              <ComparisonTable
                products={comparison.products}
                colorByProductId={colorByProductId}
                onRemove={removeProduct}
              />
              <p className="mt-2 text-xs text-muted-foreground">
                {t('pillar3.compare.totalPaidIn', { amount: formatCHF(comparison.totalPaidIn) })}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">
                {t('pillar3.compare.chartTitle', { years: yearsValue })}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <CompareChart
                products={comparison.products}
                colorByProductId={colorByProductId}
              />
              <p className="mt-4 text-xs text-muted-foreground">
                {t('pillar3.compare.footnote')}
              </p>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

function EmptyState({ text }: Readonly<{ text: string }>) {
  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
        <PackageSearch className="h-8 w-8 text-muted-foreground" />
        <p className="max-w-md text-sm text-muted-foreground">{text}</p>
      </CardContent>
    </Card>
  );
}
