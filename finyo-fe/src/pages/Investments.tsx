import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2, RefreshCw, TrendingUp, TrendingDown } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { instrumentsApi } from '@/api/instruments';
import type { Instrument, MarketData } from '@/types';

const INSTRUMENT_TYPES = ['STOCK', 'ETF', 'FUND', 'BOND', 'CRYPTO', 'OTHER'];

const SKELETON_KEYS = ['sk-1', 'sk-2', 'sk-3', 'sk-4'];

function formatMarketCap(value?: number): string {
  if (!value) return '—';
  if (value >= 1e9) return `${(value / 1e9).toFixed(1)}B`;
  if (value >= 1e6) return `${(value / 1e6).toFixed(1)}M`;
  return value.toLocaleString('de-CH');
}

function ChangeCell({ pct }: Readonly<{ pct?: number }>) {
  if (pct === undefined || pct === null) return <span className="text-muted-foreground">—</span>;
  const positive = pct >= 0;
  return (
    <span className={`flex items-center gap-0.5 ${positive ? 'text-emerald-500' : 'text-red-500'}`}>
      {positive ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
      {Math.abs(pct).toFixed(2)}%
    </span>
  );
}

function InstrumentCard({
  instrument,
  marketData,
  onDelete,
}: Readonly<{
  instrument: Instrument;
  marketData?: MarketData;
  onDelete: (id: string) => void;
}>) {
  const { t } = useTranslation();

  const displayName = marketData?.name ?? instrument.name ?? instrument.isin ?? instrument.ticker ?? instrument.valor ?? 'Unknown';

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between">
          <div>
            <CardTitle className="text-sm font-semibold leading-snug">{displayName}</CardTitle>
            <div className="flex gap-1 mt-1 flex-wrap">
              {(marketData?.instrumentType ?? instrument.instrumentType) && (
                <Badge variant="secondary" className="text-xs">
                  {marketData?.instrumentType ?? instrument.instrumentType}
                </Badge>
              )}
              {marketData?.exchange && (
                <Badge variant="outline" className="text-xs">{marketData.exchange}</Badge>
              )}
            </div>
          </div>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-muted-foreground hover:text-red-500"
            onClick={() => {
              if (globalThis.confirm('Delete this instrument?')) onDelete(instrument.id);
            }}
          >
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {marketData ? (
          <>
            {/* Price + today change */}
            <div className="flex items-baseline gap-3 mb-3">
              <span className="text-2xl font-bold">
                {marketData.currency} {marketData.lastPrice.toLocaleString('de-CH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </span>
              <ChangeCell pct={marketData.change1dPct} />
            </div>

            {/* Key facts grid */}
            <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-xs">
              <Fact label="1W" value={<ChangeCell pct={marketData.change1wPct} />} />
              <Fact label="1M" value={<ChangeCell pct={marketData.change1mPct} />} />
              <Fact label="YTD" value={<ChangeCell pct={marketData.changeYtdPct} />} />
              <Fact label="1Y" value={<ChangeCell pct={marketData.change1yPct} />} />
              {marketData.high52w != null && (
                <Fact label="52w High" value={<span>{marketData.high52w.toLocaleString('de-CH')}</span>} />
              )}
              {marketData.low52w != null && (
                <Fact label="52w Low" value={<span>{marketData.low52w.toLocaleString('de-CH')}</span>} />
              )}
              {marketData.marketCap != null && (
                <Fact label={t('investments.marketCap')} value={<span>{formatMarketCap(marketData.marketCap)}</span>} />
              )}
              {marketData.dividendYield != null && (
                <Fact label={t('investments.dividendYield')} value={<span>{marketData.dividendYield.toFixed(2)}%</span>} />
              )}
              {marketData.ter != null && (
                <Fact label="TER" value={<span>{marketData.ter.toFixed(2)}%</span>} />
              )}
              {marketData.sector && (
                <Fact label="Sector" value={<span className="truncate">{marketData.sector}</span>} />
              )}
            </div>

            {/* Identifiers */}
            <div className="mt-3 flex flex-wrap gap-2 text-xs text-muted-foreground">
              {instrument.valor && <span>{t('investments.valor')}: {instrument.valor}</span>}
              {instrument.isin && <span>ISIN: {instrument.isin}</span>}
              {instrument.ticker && <span>{t('investments.ticker')}: {instrument.ticker}</span>}
              {marketData.dataAsOf && <span className="ml-auto">As of {new Date(marketData.dataAsOf).toLocaleDateString('de-CH')}</span>}
            </div>
          </>
        ) : (
          <div className="py-4 text-center text-sm text-muted-foreground">
            <p>Market data unavailable</p>
            <div className="mt-1 flex flex-wrap gap-2 justify-center text-xs">
              {instrument.valor && <span>{t('investments.valor')}: {instrument.valor}</span>}
              {instrument.isin && <span>ISIN: {instrument.isin}</span>}
              {instrument.ticker && <span>{t('investments.ticker')}: {instrument.ticker}</span>}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function Fact({ label, value }: Readonly<{ label: string; value: React.ReactNode }>) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span>{value}</span>
    </div>
  );
}

function AddInstrumentDialog({
  open,
  onClose,
  onAdd,
  isAdding,
}: Readonly<{
  open: boolean;
  onClose: () => void;
  onAdd: (data: { valor?: string; isin?: string; ticker?: string; name?: string; instrumentType?: string }) => void;
  isAdding: boolean;
}>) {
  const { t } = useTranslation();
  const [valor, setValor] = useState('');
  const [isin, setIsin] = useState('');
  const [ticker, setTicker] = useState('');
  const [name, setName] = useState('');
  const [type, setType] = useState('STOCK');

  const handleSubmit = () => {
    onAdd({
      valor: valor || undefined,
      isin: isin || undefined,
      ticker: ticker || undefined,
      name: name || undefined,
      instrumentType: type,
    });
  };

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('investments.addInstrument')}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label>{t('investments.valor')}</Label>
            <Input value={valor} onChange={(e) => setValor(e.target.value)} placeholder="e.g. 3 (Nestlé)" />
          </div>
          <div className="space-y-2">
            <Label>{t('investments.isin')}</Label>
            <Input value={isin} onChange={(e) => setIsin(e.target.value)} placeholder="e.g. CH0012221716" />
          </div>
          <div className="space-y-2">
            <Label>{t('investments.ticker')}</Label>
            <Input value={ticker} onChange={(e) => setTicker(e.target.value)} placeholder="e.g. NESN" />
          </div>
          <div className="space-y-2">
            <Label>Name (optional)</Label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Nestlé SA" />
          </div>
          <div className="space-y-2">
            <Label>Type</Label>
            <Select value={type} onValueChange={setType}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {INSTRUMENT_TYPES.map((it) => <SelectItem key={it} value={it}>{it}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button onClick={handleSubmit} disabled={isAdding || (!valor && !isin && !ticker)}>
            {isAdding ? t('common.loading') : t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function Investments() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);

  const { data: instruments, isLoading: loadingInstruments } = useQuery({
    queryKey: ['instruments'],
    queryFn: () => instrumentsApi.getAll(token),
    enabled: !!token,
  });

  const { data: marketDataList, isLoading: loadingMarket, refetch } = useQuery({
    queryKey: ['instruments', 'market-data'],
    queryFn: () => instrumentsApi.getAllMarketData(token),
    enabled: !!token,
    staleTime: 900_000, // 15 min — matches backend cache
  });

  const { mutate: addInstrument, isPending: isAdding } = useMutation({
    mutationFn: (data: Parameters<typeof instrumentsApi.create>[1]) =>
      instrumentsApi.create(token, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['instruments'] });
      setAddOpen(false);
    },
  });

  const { mutate: deleteInstrument } = useMutation({
    mutationFn: (id: string) => instrumentsApi.delete(token, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['instruments'] }),
  });

  // Build a lookup map: valor/isin → MarketData
  const marketMap = new Map<string, MarketData>();
  (marketDataList ?? []).forEach((md) => {
    if (md.valor) marketMap.set(md.valor, md);
    if (md.isin) marketMap.set(md.isin, md);
  });

  const getMarketData = (instrument: Instrument): MarketData | undefined =>
    (instrument.valor && marketMap.get(instrument.valor)) ||
    (instrument.isin && marketMap.get(instrument.isin)) ||
    undefined;

  const isLoading = loadingInstruments || loadingMarket;
  const instrumentList = instruments ?? [];

  let content: React.ReactNode;
  if (isLoading) {
    content = (
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {SKELETON_KEYS.map((key) => <Skeleton key={key} className="h-64 w-full" />)}
      </div>
    );
  } else if (instrumentList.length === 0) {
    content = (
      <div className="py-16 text-center space-y-3">
        <p className="text-muted-foreground">{t('investments.noInstruments')}</p>
        <Button onClick={() => setAddOpen(true)}>
          <Plus className="h-4 w-4 mr-1" /> {t('investments.addInstrument')}
        </Button>
      </div>
    );
  } else {
    content = (
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {instrumentList.map((instrument) => (
          <InstrumentCard
            key={instrument.id}
            instrument={instrument}
            marketData={getMarketData(instrument)}
            onDelete={(id) => deleteInstrument(id)}
          />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('investments.title')}</h1>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RefreshCw className="h-4 w-4 mr-1" /> Refresh
          </Button>
          <Button size="sm" onClick={() => setAddOpen(true)}>
            <Plus className="h-4 w-4 mr-1" /> {t('investments.addInstrument')}
          </Button>
        </div>
      </div>

      {content}

      <AddInstrumentDialog
        open={addOpen}
        onClose={() => setAddOpen(false)}
        onAdd={(data) => addInstrument(data)}
        isAdding={isAdding}
      />
    </div>
  );
}
