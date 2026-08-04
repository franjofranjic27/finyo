import { ASSET_CLASSES } from '@/api/portfolio';
import type { AssetClass, PortfolioPosition } from '@/api/portfolio';

export interface AssetClassGroup {
  assetClass: AssetClass;
  positions: PortfolioPosition[];
  value: number;
  sharePct: number;
}

/**
 * Groups positions by asset class in display order, dropping empty groups. The group value is in
 * CHF — it sums valueChf, so an unconverted position (no rate yet) contributes nothing rather than
 * adding its foreign face value to a franc total.
 */
export function groupByAssetClass(positions: PortfolioPosition[]): AssetClassGroup[] {
  const totalValue = positions.reduce((sum, position) => sum + (position.valueChf ?? 0), 0);
  return ASSET_CLASSES.map((assetClass) => {
    const items = positions.filter((position) => position.assetClass === assetClass);
    const value = items.reduce((sum, position) => sum + (position.valueChf ?? 0), 0);
    return {
      assetClass,
      positions: items,
      value,
      sharePct: totalValue > 0 ? (value / totalValue) * 100 : 0,
    };
  }).filter((group) => group.positions.length > 0);
}
