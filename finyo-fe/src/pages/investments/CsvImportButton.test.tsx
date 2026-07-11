import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CsvImportButton } from './CsvImportButton';
import { portfolioApi } from '@/api/portfolio';
import { renderWithProviders } from '@/test/test-utils';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    user: { profile: {} },
    accessToken: 'test-token',
    roles: ['user'],
    hasRole: () => true,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock('@/api/portfolio', () => ({
  portfolioApi: {
    createPositionsBulk: vi.fn(),
  },
}));

function csvFile(content: string): File {
  return new File([content], 'positions.csv', { type: 'text/csv' });
}

describe('CsvImportButton', () => {
  beforeEach(() => {
    vi.mocked(portfolioApi.createPositionsBulk).mockResolvedValue({
      imported: 2,
      failed: 0,
      errors: [],
    });
  });

  it('parses the uploaded file and imports the positions in bulk', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CsvImportButton />);

    const file = csvFile(
      "Name;ISIN;Anzahl;Kaufkurs;Kurs\nNestlé SA;CH0038863350;1'000;90,5;\nRoche;CH0012032048;5;250;260",
    );
    await user.upload(screen.getByLabelText('Import CSV'), file);

    await waitFor(() =>
      expect(portfolioApi.createPositionsBulk).toHaveBeenCalledWith('test-token', [
        { name: 'Nestlé SA', isin: 'CH0038863350', quantity: 1000, purchasePrice: 90.5, currentPrice: undefined },
        { name: 'Roche', isin: 'CH0012032048', quantity: 5, purchasePrice: 250, currentPrice: 260 },
      ]),
    );
  });

  it('shows the import result and invalidates the portfolio query', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderWithProviders(<CsvImportButton />);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.upload(
      screen.getByLabelText('Import CSV'),
      csvFile('Nestlé SA;CH0038863350;10;90;\nRoche;CH0012032048;5;250;'),
    );

    expect(await screen.findByText('2 imported, 0 failed')).toBeInTheDocument();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['portfolio'] });
  });

  it('lists backend row errors from the bulk result', async () => {
    vi.mocked(portfolioApi.createPositionsBulk).mockResolvedValue({
      imported: 1,
      failed: 1,
      errors: ['Row 2: quantity must be a positive number'],
    });
    const user = userEvent.setup();
    renderWithProviders(<CsvImportButton />);

    await user.upload(
      screen.getByLabelText('Import CSV'),
      csvFile('Nestlé SA;CH0038863350;10;90;\nRoche;CH0012032048;5;250;'),
    );

    expect(await screen.findByText('1 imported, 1 failed')).toBeInTheDocument();
    expect(screen.getByText('Row 2: quantity must be a positive number')).toBeInTheDocument();
  });

  it('shows parse errors and skips the import when no row is valid', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CsvImportButton />);

    await user.upload(screen.getByLabelText('Import CSV'), csvFile(';;5;90;'));

    expect(await screen.findByText('No valid rows found in the file')).toBeInTheDocument();
    expect(screen.getByText('Line 1: name or isin is required')).toBeInTheDocument();
    expect(portfolioApi.createPositionsBulk).not.toHaveBeenCalled();
  });
});
