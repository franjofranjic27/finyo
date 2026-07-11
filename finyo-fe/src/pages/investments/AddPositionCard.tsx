import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/auth/useAuth';
import { portfolioApi } from '@/api/portfolio';
import { CsvImportButton } from './CsvImportButton';

export function AddPositionCard() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [name, setName] = useState('');
  const [isin, setIsin] = useState('');
  const [valor, setValor] = useState('');
  const [quantity, setQuantity] = useState('');
  const [purchasePrice, setPurchasePrice] = useState('');
  const [currentPrice, setCurrentPrice] = useState('');

  const addPosition = useMutation({
    mutationFn: () =>
      portfolioApi.createPosition(token, {
        name: name || undefined,
        isin: isin || undefined,
        valor: valor || undefined,
        quantity: Number.parseFloat(quantity),
        purchasePrice: Number.parseFloat(purchasePrice),
        currentPrice: currentPrice ? Number.parseFloat(currentPrice) : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      setName('');
      setIsin('');
      setValor('');
      setQuantity('');
      setPurchasePrice('');
      setCurrentPrice('');
    },
  });

  const hasIdentifier = Boolean(name || isin || valor);
  const canSubmit = hasIdentifier && Boolean(quantity) && Boolean(purchasePrice);

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between space-y-0">
        <CardTitle className="text-base">{t('investments.add.title')}</CardTitle>
        <CsvImportButton />
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
          <div className="space-y-2">
            <Label htmlFor="position-name">{t('investments.add.name')}</Label>
            <Input
              id="position-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
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
        </div>

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
