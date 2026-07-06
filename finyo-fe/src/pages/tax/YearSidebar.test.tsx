import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useLocation } from 'react-router-dom';
import { YearSidebar } from './YearSidebar';
import { buildYearList } from './taxYearUtils';
import { taxYearSummary } from '@/test/fixtures/tax';
import { renderWithProviders } from '@/test/test-utils';

function LocationProbe() {
  const { pathname } = useLocation();
  return <span data-testid="location">{pathname}</span>;
}

const entries = buildYearList(
  [taxYearSummary({ year: 2024, status: 'FILED' }), taxYearSummary({ year: 2023, status: 'PAID' })],
  2025,
);

describe('YearSidebar', () => {
  it('lists all years with their status badges (vertical)', () => {
    renderWithProviders(<YearSidebar entries={entries} selectedYear={2025} />);

    expect(screen.getByText('Tax Years')).toBeInTheDocument();
    for (const year of ['2026', '2025', '2024', '2023']) {
      expect(screen.getByText(year)).toBeInTheDocument();
    }
    expect(screen.getByText('Planned')).toBeInTheDocument();
    expect(screen.getByText('Current')).toBeInTheDocument();
    expect(screen.getByText('Filed')).toBeInTheDocument();
    expect(screen.getByText('Paid')).toBeInTheDocument();
  });

  it('disables the planned future year', () => {
    renderWithProviders(<YearSidebar entries={entries} selectedYear={2025} />);

    expect(screen.getByRole('button', { name: /2026/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /2024/ })).toBeEnabled();
  });

  it('navigates to the year route on click', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <>
        <YearSidebar entries={entries} selectedYear={2025} />
        <LocationProbe />
      </>,
    );

    await user.click(screen.getByRole('button', { name: /2024/ }));

    expect(screen.getByTestId('location')).toHaveTextContent('/tax/2024');
  });

  it('renders the horizontal chip strip without the sidebar title', () => {
    renderWithProviders(
      <YearSidebar entries={entries} selectedYear={2024} orientation="horizontal" />,
    );

    expect(screen.queryByText('Tax Years')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /2024/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /2026/ })).toBeDisabled();
  });
});
