import { Fragment } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useBreadcrumbSegments, type BreadcrumbSegment } from './BreadcrumbContext';

const ROUTE_LABELS: Record<string, string> = {
  '/dashboard': 'nav.dashboard',
  '/wealth': 'nav.wealth',
  // The investments page title is "Portfolio"; the sidebar keeps nav.investments.
  '/investments': 'breadcrumb.portfolio',
  '/tax': 'nav.tax',
  '/pillar3': 'nav.pillar3',
  '/budget': 'nav.budget',
  '/insurance': 'nav.insurance',
  '/accounts': 'nav.accounts',
  '/documents': 'nav.documents',
  '/settings': 'nav.settings',
  '/admin': 'nav.admin',
};

/**
 * Content-area breadcrumb: muted, clickable parent segments separated by "/",
 * the current segment rendered bold as the page heading.
 */
export function Breadcrumb({ segments }: Readonly<{ segments: BreadcrumbSegment[] }>) {
  const current = segments.at(-1);
  if (!current) return null;
  const parents = segments.slice(0, -1);

  return (
    <nav aria-label="Breadcrumb" className="flex min-w-0 flex-1 items-center gap-2 text-lg">
      {parents.map((segment) => (
        <Fragment key={`${segment.label}|${segment.to ?? ''}`}>
          {segment.to ? (
            <Link
              to={segment.to}
              className="shrink-0 text-muted-foreground transition-colors hover:text-foreground"
            >
              {segment.label}
            </Link>
          ) : (
            <span className="shrink-0 text-muted-foreground">{segment.label}</span>
          )}
          <span className="shrink-0 text-muted-foreground" aria-hidden="true">
            /
          </span>
        </Fragment>
      ))}
      <h1 className="truncate font-semibold text-foreground">{current.label}</h1>
    </nav>
  );
}

/**
 * Breadcrumb for the main content area: renders the segments published by the
 * active page, falling back to a single segment derived from the first path
 * segment (so nested routes like /tax/2025 resolve correctly).
 */
export function PageBreadcrumb() {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const contextSegments = useBreadcrumbSegments();
  const segments = contextSegments ?? [
    { label: t(ROUTE_LABELS[`/${pathname.split('/')[1]}`] ?? 'nav.dashboard') },
  ];
  return <Breadcrumb segments={segments} />;
}
