/**
 * Palette for product swatches and chart bars — assigned by selection index
 * (indigo, violet, emerald, amber, rose, sky, teal).
 */
const PRODUCT_COLORS = [
  '#6366f1',
  '#8b5cf6',
  '#10b981',
  '#f59e0b',
  '#f43f5e',
  '#0ea5e9',
  '#14b8a6',
] as const;

export function productColor(selectionIndex: number): string {
  return PRODUCT_COLORS[selectionIndex % PRODUCT_COLORS.length];
}
