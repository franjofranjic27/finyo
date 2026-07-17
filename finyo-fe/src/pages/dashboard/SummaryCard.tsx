import type { ComponentType } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

interface SummaryCardProps {
  title: string;
  value: string;
  sub?: string;
  icon: ComponentType<{ className?: string }>;
  valueClass?: string;
}

/** Small KPI tile: title, icon, a big value and an optional sub line. */
export function SummaryCard({
  title,
  value,
  sub,
  icon: Icon,
  valueClass,
}: Readonly<SummaryCardProps>) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between px-4 pb-2 pt-4 lg:px-6 lg:pt-6">
        <CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle>
        <Icon className="h-4 w-4 shrink-0 text-muted-foreground" />
      </CardHeader>
      <CardContent className="px-4 pb-4 lg:px-6 lg:pb-6">
        <p className={`break-words text-xl font-bold tabular-nums lg:text-2xl ${valueClass ?? 'text-foreground'}`}>
          {value}
        </p>
        {sub && <p className="mt-1 text-xs text-muted-foreground">{sub}</p>}
      </CardContent>
    </Card>
  );
}

export function SummaryCardSkeleton() {
  return (
    <Card>
      <CardHeader className="pb-2">
        <Skeleton className="h-4 w-28" />
      </CardHeader>
      <CardContent>
        <Skeleton className="h-8 w-36" />
        <Skeleton className="mt-2 h-3 w-24" />
      </CardContent>
    </Card>
  );
}
