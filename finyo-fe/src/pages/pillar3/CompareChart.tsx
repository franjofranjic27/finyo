import {
  Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import type { Pillar3ProductComparison } from '@/api/pillar3';
import { formatCHF } from '@/lib/formatters';

interface CompareChartProps {
  /** Sorted by finalCapital descending, matching the comparison table. */
  products: Pillar3ProductComparison[];
  colorByProductId: ReadonlyMap<string, string>;
}

const BAR_HEIGHT = 44;

export function CompareChart({ products, colorByProductId }: Readonly<CompareChartProps>) {
  const data = products.map((product) => ({
    productId: product.productId,
    name: product.name,
    finalCapital: Math.round(product.finalCapital),
    color: colorByProductId.get(product.productId) ?? 'hsl(var(--primary))',
  }));

  return (
    <ResponsiveContainer width="100%" height={data.length * BAR_HEIGHT + 40}>
      <BarChart data={data} layout="vertical" margin={{ left: 8, right: 24 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" horizontal={false} />
        <XAxis
          type="number"
          tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }}
          axisLine={false}
          tickLine={false}
          tickFormatter={(v: number) => `${(v / 1000).toFixed(0)}k`}
        />
        <YAxis
          type="category"
          dataKey="name"
          width={180}
          tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }}
          axisLine={false}
          tickLine={false}
        />
        <Tooltip
          formatter={(v: number) => formatCHF(v)}
          contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))' }}
          cursor={{ fill: 'hsl(var(--muted))', fillOpacity: 0.4 }}
        />
        <Bar dataKey="finalCapital" radius={[0, 4, 4, 0]} barSize={24}>
          {data.map((entry) => (
            <Cell key={entry.productId} fill={entry.color} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
