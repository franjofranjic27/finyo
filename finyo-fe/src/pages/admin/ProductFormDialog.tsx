import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { useAuth } from '@/auth/useAuth';
import { pillar3Api } from '@/api/pillar3';
import type { Pillar3Product, Pillar3ProductInput } from '@/api/pillar3';

// Mirrors the backend validation (format only, no checksum).
const ISIN_PATTERN = /^[A-Za-z]{2}[A-Za-z0-9]{9}[0-9]$/;
const VALOR_PATTERN = /^\d{1,20}$/;

type FieldErrors = Partial<
  Record<'provider' | 'name' | 'isin' | 'valor' | 'equityPct' | 'terPct', string>
>;

interface ProductFormDialogProps {
  /** null = create, otherwise edit. Mount the dialog fresh per open. */
  product: Pillar3Product | null;
  onClose: () => void;
}

export function ProductFormDialog({ product, onClose }: Readonly<ProductFormDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [provider, setProvider] = useState(product?.provider ?? '');
  const [name, setName] = useState(product?.name ?? '');
  const [isin, setIsin] = useState(product?.isin ?? '');
  const [valor, setValor] = useState(product?.valor ?? '');
  const [equityPct, setEquityPct] = useState(product ? String(product.equityPct) : '');
  const [terPct, setTerPct] = useState(product ? String(product.terPct) : '');
  const [sortOrder, setSortOrder] = useState(product ? String(product.sortOrder) : '0');
  const [errors, setErrors] = useState<FieldErrors>({});

  const save = useMutation({
    mutationFn: (input: Pillar3ProductInput) =>
      product
        ? pillar3Api.updateProduct(token, product.id, input)
        : pillar3Api.createProduct(token, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pillar3-products'] });
      onClose();
    },
  });

  const validate = (): FieldErrors => {
    const result: FieldErrors = {};
    if (!provider.trim()) result.provider = t('admin.products.validation.providerRequired');
    if (!name.trim()) result.name = t('admin.products.validation.nameRequired');
    if (!ISIN_PATTERN.test(isin.trim())) {
      result.isin = t('admin.products.validation.isinInvalid');
    }
    if (valor.trim() && !VALOR_PATTERN.test(valor.trim())) {
      result.valor = t('admin.products.validation.valorInvalid');
    }
    const equity = Number.parseFloat(equityPct);
    if (Number.isNaN(equity) || equity < 0 || equity > 100) {
      result.equityPct = t('admin.products.validation.equityRange');
    }
    const ter = Number.parseFloat(terPct);
    if (Number.isNaN(ter) || ter < 0 || ter > 10) {
      result.terPct = t('admin.products.validation.terRange');
    }
    return result;
  };

  const handleSave = () => {
    const validationErrors = validate();
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    save.mutate({
      provider: provider.trim(),
      name: name.trim(),
      isin: isin.trim().toUpperCase(),
      valor: valor.trim() || null,
      equityPct: Number.parseFloat(equityPct),
      terPct: Number.parseFloat(terPct),
      active: product?.active ?? true,
      sortOrder: Number.parseInt(sortOrder) || 0,
    });
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {product ? t('admin.products.editProduct') : t('admin.products.newProduct')}
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="product-provider">{t('admin.products.provider')}</Label>
            <Input
              id="product-provider"
              value={provider}
              onChange={(e) => setProvider(e.target.value)}
            />
            {errors.provider && <p className="text-sm text-destructive">{errors.provider}</p>}
          </div>
          <div className="space-y-2">
            <Label htmlFor="product-name">{t('admin.products.name')}</Label>
            <Input id="product-name" value={name} onChange={(e) => setName(e.target.value)} />
            {errors.name && <p className="text-sm text-destructive">{errors.name}</p>}
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="product-isin">{t('admin.products.isin')}</Label>
              <Input
                id="product-isin"
                value={isin}
                maxLength={12}
                onChange={(e) => setIsin(e.target.value)}
              />
              {errors.isin && <p className="text-sm text-destructive">{errors.isin}</p>}
            </div>
            <div className="space-y-2">
              <Label htmlFor="product-valor">{t('admin.products.valor')}</Label>
              <Input
                id="product-valor"
                value={valor}
                maxLength={20}
                onChange={(e) => setValor(e.target.value)}
              />
              {errors.valor && <p className="text-sm text-destructive">{errors.valor}</p>}
            </div>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <div className="space-y-2">
              <Label htmlFor="product-equity">{t('admin.products.equity')} (%)</Label>
              <Input
                id="product-equity"
                type="number"
                min={0}
                max={100}
                step={0.01}
                value={equityPct}
                onChange={(e) => setEquityPct(e.target.value)}
              />
              {errors.equityPct && <p className="text-sm text-destructive">{errors.equityPct}</p>}
            </div>
            <div className="space-y-2">
              <Label htmlFor="product-ter">{t('admin.products.ter')} (%)</Label>
              <Input
                id="product-ter"
                type="number"
                min={0}
                max={10}
                step={0.001}
                value={terPct}
                onChange={(e) => setTerPct(e.target.value)}
              />
              {errors.terPct && <p className="text-sm text-destructive">{errors.terPct}</p>}
            </div>
            <div className="space-y-2">
              <Label htmlFor="product-sort-order">{t('admin.products.sortOrder')}</Label>
              <Input
                id="product-sort-order"
                type="number"
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value)}
              />
            </div>
          </div>
          {/* Server errors, e.g. the 409 ProblemDetail message on ISIN duplicates. */}
          {save.error && <p className="text-sm text-destructive">{save.error.message}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleSave} disabled={save.isPending}>
            {save.isPending ? t('common.loading') : t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
