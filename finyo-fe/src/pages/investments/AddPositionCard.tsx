import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Check, Info, Loader2, Plus } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/auth/useAuth';
import { portfolioApi, type InstrumentLookup } from '@/api/portfolio';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { CsvImportButton } from './CsvImportButton';

// A complete ISIN (2 country letters, 9 alphanumerics, 1 check digit) or a valor (digits).
// The lookup only fires once the identifier is plausibly complete, so a half-typed ISIN does
// not produce a stream of "not found" previews.
const ISIN_PATTERN = /^[A-Za-z]{2}[A-Za-z0-9]{9}[0-9]$/;
const VALOR_PATTERN = /^\d{4,12}$/;

function activeIdentifier(isin: string, valor: string): { isin?: string; valor?: string } | null {
  const trimmedIsin = isin.trim().toUpperCase();
  if (ISIN_PATTERN.test(trimmedIsin)) return { isin: trimmedIsin };
  const trimmedValor = valor.trim();
  if (VALOR_PATTERN.test(trimmedValor)) return { valor: trimmedValor };
  return null;
}

export function AddPositionCard() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [name, setName] = useState('');
  const [nameEdited, setNameEdited] = useState(false);
  const [isin, setIsin] = useState('');
  const [valor, setValor] = useState('');
  const [quantity, setQuantity] = useState('');
  const [purchasePrice, setPurchasePrice] = useState('');
  const [currentPrice, setCurrentPrice] = useState('');

  // Debounced so the providers are queried when the user pauses, not on every keystroke.
  const debouncedIsin = useDebouncedValue(isin, 400);
  const debouncedValor = useDebouncedValue(valor, 400);
  const identifier = activeIdentifier(debouncedIsin, debouncedValor);

  const lookup = useQuery({
    queryKey: ['instrument-lookup', identifier?.isin, identifier?.valor],
    queryFn: () => portfolioApi.lookupInstrument(token, identifier!),
    enabled: Boolean(token && identifier),
    staleTime: 5 * 60 * 1000,
  });

  const result = identifier ? lookup.data : undefined;
  const isFound = result?.status === 'FOUND';

  // Derived, not synced into state: the name shows the looked-up value until the user types their
  // own, and a listed security's price comes automatically so any manual price is ignored. Both
  // avoid the setState-in-effect cascade — the display and the submit read the same expressions.
  const effectiveName = !nameEdited && isFound && result?.name ? result.name : name;
  const submittedCurrentPrice = !isFound && currentPrice ? Number.parseFloat(currentPrice) : undefined;

  const addPosition = useMutation({
    mutationFn: () =>
      portfolioApi.createPosition(token, {
        name: effectiveName || undefined,
        isin: isin || undefined,
        valor: valor || undefined,
        quantity: Number.parseFloat(quantity),
        purchasePrice: Number.parseFloat(purchasePrice),
        currentPrice: submittedCurrentPrice,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      setName('');
      setNameEdited(false);
      setIsin('');
      setValor('');
      setQuantity('');
      setPurchasePrice('');
      setCurrentPrice('');
    },
  });

  const hasIdentifier = Boolean(effectiveName || isin || valor);
  const canSubmit = hasIdentifier && Boolean(quantity) && Boolean(purchasePrice);

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between space-y-0">
        <CardTitle className="text-base">{t('investments.add.title')}</CardTitle>
        <CsvImportButton />
      </CardHeader>
      <CardContent className="space-y-4">
        <div
          className={`grid grid-cols-2 gap-4 md:grid-cols-3 ${isFound ? 'lg:grid-cols-5' : 'lg:grid-cols-6'}`}
        >
          <div className="space-y-2">
            <Label htmlFor="position-name">{t('investments.add.name')}</Label>
            <Input
              id="position-name"
              value={effectiveName}
              onChange={(e) => {
                setName(e.target.value);
                setNameEdited(true);
              }}
              placeholder="Nestlé SA"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="position-isin">{t('investments.add.isin')}</Label>
            <Input
              id="position-isin"
              value={isin}
              onChange={(e) => setIsin(e.target.value)}
              placeholder="CH0038863350"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="position-valor">{t('investments.add.valor')}</Label>
            <Input
              id="position-valor"
              value={valor}
              onChange={(e) => setValor(e.target.value)}
              placeholder="3886335"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="position-quantity">{t('investments.add.quantity')}</Label>
            <Input
              id="position-quantity"
              type="number"
              min={0}
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="position-purchase-price">{t('investments.add.purchasePrice')}</Label>
            <Input
              id="position-purchase-price"
              type="number"
              min={0}
              value={purchasePrice}
              onChange={(e) => setPurchasePrice(e.target.value)}
            />
          </div>
          {!isFound && (
            <div className="space-y-2">
              <Label htmlFor="position-current-price">{t('investments.add.currentPrice')}</Label>
              <Input
                id="position-current-price"
                type="number"
                min={0}
                value={currentPrice}
                onChange={(e) => setCurrentPrice(e.target.value)}
              />
            </div>
          )}
        </div>

        <LookupPreview
          isLoading={Boolean(identifier) && lookup.isLoading}
          result={result}
        />

        {addPosition.error && (
          <p className="text-sm text-destructive">{addPosition.error.message}</p>
        )}

        <div className="flex items-center justify-between gap-4">
          <div className="space-y-0.5 text-xs text-muted-foreground">
            <p>{t('investments.add.identifierHint')}</p>
            <p>{t('investments.add.csvHint')}</p>
          </div>
          <Button
            size="sm"
            onClick={() => addPosition.mutate()}
            disabled={!canSubmit || addPosition.isPending}
          >
            <Plus className="mr-1 h-4 w-4" />
            {addPosition.isPending ? t('common.loading') : t('investments.add.submit')}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function LookupPreview({ isLoading, result }: { isLoading: boolean; result: InstrumentLookup | undefined }) {
  const { t } = useTranslation();

  if (isLoading) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" />
        {t('investments.add.lookup.searching')}
      </p>
    );
  }
  if (!result) return null;

  if (result.status === 'FOUND') {
    const detail = [result.assetClass, result.currency].filter(Boolean).join(' · ');
    return (
      <p className="flex flex-wrap items-center gap-2 text-sm text-emerald-600 dark:text-emerald-400">
        <Check className="h-4 w-4 shrink-0" />
        <span className="font-medium text-foreground">{result.name}</span>
        {detail && <span className="text-muted-foreground">· {detail}</span>}
        <span className="text-muted-foreground">— {t('investments.add.lookup.priceAuto')}</span>
      </p>
    );
  }
  if (result.status === 'NOT_FOUND') {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Info className="h-4 w-4 shrink-0" />
        {t('investments.add.lookup.notFound')}
      </p>
    );
  }
  return (
    <p className="flex items-center gap-2 text-sm text-amber-600 dark:text-amber-400">
      <AlertTriangle className="h-4 w-4 shrink-0" />
      {t('investments.add.lookup.unavailable')}
    </p>
  );
}
