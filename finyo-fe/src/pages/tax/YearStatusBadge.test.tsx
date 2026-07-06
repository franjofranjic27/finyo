import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { YearStatusBadge } from './YearStatusBadge';

describe('YearStatusBadge', () => {
  it.each([
    ['planned', 'Planned'],
    ['current', 'Current'],
    ['OPEN', 'Open'],
    ['FILED', 'Filed'],
    ['ASSESSED', 'Assessed'],
    ['PAID', 'Paid'],
  ] as const)('renders the label for status %s', (status, label) => {
    render(<YearStatusBadge status={status} />);

    expect(screen.getByText(label)).toBeInTheDocument();
  });
});
