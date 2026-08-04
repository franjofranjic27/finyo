import type { TFunction } from 'i18next';
import type { WealthBucket } from '@/api/wealth';

/**
 * Auto rows are rendered under a translated name (instead of the backend's
 * default string) so language switching works for them like for any label.
 * Shared by the wealth page and the dashboard legend — the same bucket must
 * carry the same name everywhere.
 */
export function bucketDisplayName(bucket: WealthBucket, t: TFunction): string {
  if (!bucket.auto) {
    return bucket.name;
  }
  return bucket.source === 'PILLAR3'
    ? t('wealth.buckets.autoPillar3')
    : t('wealth.buckets.autoPortfolio');
}
