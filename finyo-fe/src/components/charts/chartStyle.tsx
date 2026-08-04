import type { CSSProperties } from 'react';
import { formatCHF } from '@/lib/formatters';

/** Shared Recharts styling for the finance charts (grid, axes, tooltip, legend). */

export const chartGridProps = {
  strokeDasharray: '3 3',
  stroke: 'hsl(var(--border))',
} as const;

export const chartAxisProps = {
  tick: { fill: 'hsl(var(--muted-foreground))', fontSize: 11 },
  axisLine: false,
  tickLine: false,
} as const;

/** Axis ticks as compact thousands, e.g. 250000 -> "250k". */
export const formatThousandsTick = (v: number): string => `${(v / 1000).toFixed(0)}k`;

export const formatTooltipCHF = (v: number): string => formatCHF(v);

export const chartTooltipStyle: CSSProperties = {
  background: 'hsl(var(--card))',
  border: '1px solid hsl(var(--border))',
};

// Pie tooltips need this: their payload carries no series colour, so recharts falls back
// to #000 item text — invisible on the dark card background. Line/bar tooltips keep the
// series colour and stay readable without it.
export const chartTooltipItemStyle: CSSProperties = {
  color: 'hsl(var(--foreground))',
};

// Module scope: recharts re-renders formatter results, nested components would remount.
export const renderLegendText = (value: string) => (
  <span style={{ color: 'hsl(var(--foreground))', fontSize: 12 }}>{value}</span>
);
