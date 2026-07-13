import type { RangeCategoryBreakdown } from '@/api/analytics';

/**
 * The backend labels the null-category group "Uncategorized" in English — key
 * off the id so the label follows the UI language.
 */
export function categoryLabel(item: RangeCategoryBreakdown, uncategorized: string): string {
  return item.categoryId === null ? uncategorized : (item.categoryName ?? uncategorized);
}

/** Spending as a positive magnitude; the sign depends on the endpoint. */
export function categoryAmount(item: RangeCategoryBreakdown): number {
  return Math.abs(Number(item.total));
}

export function biggestCategory(
  items: readonly RangeCategoryBreakdown[],
): RangeCategoryBreakdown | null {
  return items.reduce<RangeCategoryBreakdown | null>((biggest, item) => {
    if (!biggest || categoryAmount(item) > categoryAmount(biggest)) return item;
    return biggest;
  }, null);
}
