import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Power, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { useAuth } from '@/auth/useAuth';
import { pillar3Api } from '@/api/pillar3';
import type { Pillar3Product, Pillar3ProductInput } from '@/api/pillar3';
import { formatPercent } from '@/lib/formatters';
import { ProductFormDialog } from './ProductFormDialog';

function toInput(product: Pillar3Product): Pillar3ProductInput {
  return {
    provider: product.provider,
    name: product.name,
    isin: product.isin,
    valor: product.valor,
    equityPct: product.equityPct,
    terPct: product.terPct,
    active: product.active,
    sortOrder: product.sortOrder,
  };
}

export function Pillar3ProductsAdminPage() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Pillar3Product | null>(null);
  const [deleting, setDeleting] = useState<Pillar3Product | null>(null);

  const { data: products, isLoading } = useQuery({
    queryKey: ['pillar3-products', 'admin'],
    queryFn: () => pillar3Api.adminList(token),
    enabled: !!token,
  });

  // Invalidates both the admin list and the user-facing catalog.
  const invalidateProducts = () =>
    queryClient.invalidateQueries({ queryKey: ['pillar3-products'] });

  const toggleActive = useMutation({
    mutationFn: (product: Pillar3Product) =>
      pillar3Api.updateProduct(token, product.id, {
        ...toInput(product),
        active: !product.active,
      }),
    onSuccess: () => {
      invalidateProducts();
      setDeleting(null);
    },
  });

  const deleteProduct = useMutation({
    mutationFn: (id: string) => pillar3Api.deleteProduct(token, id),
    onSuccess: () => {
      invalidateProducts();
      setDeleting(null);
    },
  });

  const openCreate = () => {
    setEditing(null);
    setFormOpen(true);
  };

  const openEdit = (product: Pillar3Product) => {
    setEditing(product);
    setFormOpen(true);
  };

  const closeForm = () => {
    setFormOpen(false);
    setEditing(null);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('admin.products.title')}</h1>
        <Button onClick={openCreate}>
          <Plus className="mr-2 h-4 w-4" />
          {t('admin.products.newProduct')}
        </Button>
      </div>

      {isLoading && <Skeleton className="h-64 w-full" />}

      {!isLoading && (products ?? []).length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center gap-4 py-12 text-center">
            <p className="max-w-md text-sm text-muted-foreground">{t('admin.products.empty')}</p>
            <Button onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              {t('admin.products.newProduct')}
            </Button>
          </CardContent>
        </Card>
      )}

      {(products ?? []).length > 0 && (
        <Card>
          <CardContent className="pt-4">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('admin.products.provider')}</TableHead>
                  <TableHead>{t('admin.products.name')}</TableHead>
                  <TableHead>{t('admin.products.isin')}</TableHead>
                  <TableHead>{t('admin.products.valor')}</TableHead>
                  <TableHead className="text-right">{t('admin.products.equity')}</TableHead>
                  <TableHead className="text-right">{t('admin.products.ter')}</TableHead>
                  <TableHead>{t('admin.products.active')}</TableHead>
                  <TableHead className="text-right">{t('admin.products.actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(products ?? []).map((product) => (
                  <TableRow key={product.id} className={product.active ? '' : 'opacity-60'}>
                    <TableCell>{product.provider}</TableCell>
                    <TableCell className="font-medium">{product.name}</TableCell>
                    <TableCell className="text-muted-foreground">{product.isin}</TableCell>
                    <TableCell className="text-muted-foreground">{product.valor ?? '—'}</TableCell>
                    <TableCell className="text-right">{formatPercent(product.equityPct)}</TableCell>
                    <TableCell className="text-right">{product.terPct.toFixed(2)}%</TableCell>
                    <TableCell>
                      {product.active ? (
                        <Badge className="bg-emerald-500 hover:bg-emerald-500">
                          {t('admin.products.active')}
                        </Badge>
                      ) : (
                        <Badge variant="secondary">{t('admin.products.inactive')}</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8"
                          aria-label={t('common.edit')}
                          onClick={() => openEdit(product)}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8"
                          aria-label={
                            product.active
                              ? t('admin.products.deactivate')
                              : t('admin.products.activate')
                          }
                          title={
                            product.active
                              ? t('admin.products.deactivate')
                              : t('admin.products.activate')
                          }
                          disabled={toggleActive.isPending}
                          onClick={() => toggleActive.mutate(product)}
                        >
                          <Power className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8 text-muted-foreground hover:text-destructive"
                          aria-label={t('common.delete')}
                          onClick={() => setDeleting(product)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {formOpen && <ProductFormDialog product={editing} onClose={closeForm} />}

      {/* Delete confirmation — nudges towards deactivating instead. */}
      <Dialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t('admin.products.deleteTitle')}</DialogTitle>
            <DialogDescription>
              {t('admin.products.deleteText', { name: deleting?.name })}
            </DialogDescription>
          </DialogHeader>
          {deleteProduct.error && (
            <p className="text-sm text-destructive">{deleteProduct.error.message}</p>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleting(null)}>
              {t('common.cancel')}
            </Button>
            {deleting?.active && (
              <Button
                variant="outline"
                disabled={toggleActive.isPending}
                onClick={() => toggleActive.mutate(deleting)}
              >
                {t('admin.products.deactivateInstead')}
              </Button>
            )}
            <Button
              variant="destructive"
              disabled={deleteProduct.isPending}
              onClick={() => deleting && deleteProduct.mutate(deleting.id)}
            >
              {deleteProduct.isPending ? t('common.loading') : t('common.delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
