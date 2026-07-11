import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Search } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import type { Pillar3Product } from '@/api/pillar3';

const MAX_RESULTS = 8;

interface ProductSearchComboboxProps {
  products: Pillar3Product[];
  selectedIds: string[];
  onSelect: (product: Pillar3Product) => void;
  disabled?: boolean;
}

function matchesQuery(product: Pillar3Product, query: string): boolean {
  return [product.name, product.provider, product.isin, product.valor ?? '']
    .some((field) => field.toLowerCase().includes(query));
}

/**
 * Lightweight combobox: input + absolutely positioned result card,
 * filtering the already-loaded catalog client-side (no cmdk dependency).
 */
export function ProductSearchCombobox({
  products,
  selectedIds,
  onSelect,
  disabled = false,
}: Readonly<ProductSearchComboboxProps>) {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);

  const normalizedQuery = query.trim().toLowerCase();
  const results = products
    .filter((product) => !selectedIds.includes(product.id))
    .filter((product) => !normalizedQuery || matchesQuery(product, normalizedQuery))
    .slice(0, MAX_RESULTS);

  const pick = (product: Pillar3Product) => {
    onSelect(product);
    setQuery('');
    setOpen(false);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Escape') {
      setOpen(false);
    } else if (event.key === 'Enter' && open && results.length > 0) {
      event.preventDefault();
      pick(results[0]);
    }
  };

  return (
    <div className="relative">
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
      <Input
        value={query}
        disabled={disabled}
        placeholder={t('pillar3.compare.searchPlaceholder')}
        aria-label={t('pillar3.compare.searchLabel')}
        className="pl-9"
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onClick={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={handleKeyDown}
      />
      {open && !disabled && (
        <Card className="absolute z-20 mt-1 w-full overflow-hidden p-1 shadow-lg">
          {results.length === 0 && (
            <p className="px-3 py-2 text-sm text-muted-foreground">
              {t('pillar3.compare.noResults')}
            </p>
          )}
          {results.map((product) => (
            <button
              key={product.id}
              type="button"
              className="flex w-full items-center justify-between gap-4 rounded-sm px-3 py-2 text-left text-sm transition-colors hover:bg-accent hover:text-accent-foreground"
              // onMouseDown fires before the input blur closes the list.
              onMouseDown={(e) => {
                e.preventDefault();
                pick(product);
              }}
            >
              <span className="min-w-0">
                <span className="block truncate font-medium">{product.name}</span>
                <span className="block truncate text-xs text-muted-foreground">
                  {product.provider}
                </span>
              </span>
              <span className="shrink-0 text-right text-xs text-muted-foreground">
                <span className="block">{product.isin}</span>
                {product.valor && <span className="block">{product.valor}</span>}
              </span>
            </button>
          ))}
        </Card>
      )}
    </div>
  );
}
