import type { AccountScope } from '@/types';

/** Display order of the scope groups: private accounts first. */
export const ACCOUNT_SCOPES: readonly AccountScope[] = ['PRIVATE', 'BUSINESS'];

/** i18n key for a scope's display label. */
export function scopeLabelKey(scope: AccountScope): string {
  return scope === 'PRIVATE' ? 'accounts.scopePrivate' : 'accounts.scopeBusiness';
}
