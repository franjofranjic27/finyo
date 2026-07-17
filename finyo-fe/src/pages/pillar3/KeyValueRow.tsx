import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface KeyValueRowProps {
  label: string;
  value: ReactNode;
  /** Bold, slightly larger value for the headline figure of a card. */
  emphasized?: boolean;
}

/**
 * Label/value line used by the mobile card-list rendering of the
 * scenario and product comparison tables.
 */
export function KeyValueRow({ label, value, emphasized = false }: Readonly<KeyValueRowProps>) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className={cn('text-[13px] tabular-nums', emphasized && 'text-sm font-bold')}>
        {value}
      </dd>
    </div>
  );
}
