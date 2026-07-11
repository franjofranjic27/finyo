import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

export interface BreadcrumbSegment {
  label: string;
  /** When set, the segment renders as a link (parent segments only). */
  to?: string;
}

interface BreadcrumbContextValue {
  segments: BreadcrumbSegment[] | null;
  setSegments: (segments: BreadcrumbSegment[] | null) => void;
}

// Default no-op value so pages (and their tests) work without the provider.
const BreadcrumbContext = createContext<BreadcrumbContextValue>({
  segments: null,
  setSegments: () => {},
});

export function BreadcrumbProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [segments, setSegments] = useState<BreadcrumbSegment[] | null>(null);
  const value = useMemo(() => ({ segments, setSegments }), [segments]);
  return <BreadcrumbContext.Provider value={value}>{children}</BreadcrumbContext.Provider>;
}

/** Segments published by the active page, or null for the route-derived fallback. */
export function useBreadcrumbSegments(): BreadcrumbSegment[] | null {
  return useContext(BreadcrumbContext).segments;
}

/**
 * Publishes the page's breadcrumb to the header while the page is mounted.
 * Pass null to keep the route-derived single-segment fallback.
 */
export function useBreadcrumb(segments: BreadcrumbSegment[] | null): void {
  const { setSegments } = useContext(BreadcrumbContext);
  // Serialised so translated labels update on language change without
  // requiring pages to memoise the segment array.
  const serialised = segments === null ? null : JSON.stringify(segments);

  useEffect(() => {
    setSegments(serialised === null ? null : (JSON.parse(serialised) as BreadcrumbSegment[]));
    return () => setSegments(null);
  }, [serialised, setSegments]);
}
