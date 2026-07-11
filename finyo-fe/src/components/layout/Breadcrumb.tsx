import { Fragment } from 'react';
import { Link } from 'react-router-dom';
import type { BreadcrumbSegment } from './BreadcrumbContext';

/**
 * Top-bar breadcrumb: muted, clickable parent segments separated by "/",
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
