import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { Breadcrumb, PageBreadcrumb } from './Breadcrumb';
import { BreadcrumbProvider, useBreadcrumb, type BreadcrumbSegment } from './BreadcrumbContext';
import { renderWithProviders } from '@/test/test-utils';

function PublishSegments({ segments }: Readonly<{ segments: BreadcrumbSegment[] }>) {
  useBreadcrumb(segments);
  return null;
}

describe('Breadcrumb', () => {
  it('renders a single segment as the page heading without links', () => {
    renderWithProviders(<Breadcrumb segments={[{ label: 'Portfolio' }]} />);

    expect(screen.getByRole('heading', { name: 'Portfolio' })).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('renders parent segments as muted links and the last segment as heading', () => {
    renderWithProviders(
      <Breadcrumb
        segments={[
          { label: 'Portfolio', to: '/investments' },
          { label: 'Position – Nestlé SA' },
        ]}
      />,
    );

    const parent = screen.getByRole('link', { name: 'Portfolio' });
    expect(parent).toHaveAttribute('href', '/investments');
    expect(parent).toHaveClass('text-muted-foreground');
    expect(screen.getByRole('heading', { name: 'Position – Nestlé SA' })).toBeInTheDocument();
    expect(screen.getByText('/')).toBeInTheDocument();
  });

  it('renders parents without a target as plain muted text', () => {
    renderWithProviders(
      <Breadcrumb segments={[{ label: 'Taxes' }, { label: 'Tax year 2025' }]} />,
    );

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(screen.getByText('Taxes')).toHaveClass('text-muted-foreground');
    expect(screen.getByRole('heading', { name: 'Tax year 2025' })).toBeInTheDocument();
  });

  it('renders nothing for an empty segment list', () => {
    const { container } = renderWithProviders(<Breadcrumb segments={[]} />);

    expect(container).toBeEmptyDOMElement();
  });
});

describe('PageBreadcrumb', () => {
  it('falls back to the route-derived title on nested routes', () => {
    renderWithProviders(<PageBreadcrumb />, { route: '/tax/2025' });

    expect(screen.getByRole('heading', { name: 'Taxes' })).toBeInTheDocument();
  });

  it('renders the segments published by the active page', () => {
    renderWithProviders(
      <BreadcrumbProvider>
        <PublishSegments
          segments={[{ label: 'Portfolio', to: '/investments' }, { label: 'Position – Nestlé SA' }]}
        />
        <PageBreadcrumb />
      </BreadcrumbProvider>,
      { route: '/investments/positions/1' },
    );

    expect(screen.getByRole('link', { name: 'Portfolio' })).toHaveAttribute('href', '/investments');
    expect(screen.getByRole('heading', { name: 'Position – Nestlé SA' })).toBeInTheDocument();
  });
});
