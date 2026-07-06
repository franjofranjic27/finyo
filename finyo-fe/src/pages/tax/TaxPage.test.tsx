import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { TaxPage } from './TaxPage';
import { taxApi } from '@/api/tax';
import { taxResult, taxYearDetail, taxYearSummary } from '@/test/fixtures/tax';
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

vi.mock('@/api/tax', () => ({
  taxApi: {
    getYears: vi.fn(),
    getYear: vi.fn(),
    getCommunes: vi.fn(),
    upsertYear: vi.fn(),
    updateYearStatus: vi.fn(),
    deleteYear: vi.fn(),
    addPayment: vi.fn(),
    deletePayment: vi.fn(),
    addDeadline: vi.fn(),
    updateDeadline: vi.fn(),
    deleteDeadline: vi.fn(),
  },
}));

function renderTaxPage(route: string) {
  return renderWithProviders(<TaxPage />, { route, path: '/tax/:year?' });
}

describe('TaxPage', () => {
  it('renders a persisted year with calculation, payments and deadlines', async () => {
    vi.mocked(taxApi.getYears).mockResolvedValue([
      taxYearSummary({
        year: 2024,
        status: 'FILED',
        grossIncome: 100_000,
        expectedTax: 12_000,
        effectiveRatePercent: 12,
      }),
      taxYearSummary({
        year: 2023,
        status: 'PAID',
        grossIncome: 95_000,
        expectedTax: 11_000,
        effectiveRatePercent: 11.6,
      }),
    ]);
    vi.mocked(taxApi.getYear).mockResolvedValue(
      taxYearDetail({
        year: 2024,
        status: 'FILED',
        calculation: taxResult({ taxYear: 2024 }),
        expectedTax: 12_000,
        paidTotal: 6000,
        openAmount: 6000,
        payments: [{ id: 'p1', paymentDate: '2024-03-01', amount: 6000, label: 'Instalment' }],
        deadlines: [
          { id: 'd1', dueDate: '2024-03-31', label: 'File return', amount: null, done: false },
        ],
      }),
    );
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);

    renderTaxPage('/tax/2024');

    expect(await screen.findByRole('heading', { name: 'Tax Year 2024' })).toBeInTheDocument();
    expect(taxApi.getYear).toHaveBeenCalledWith('test-token', 2024);
    // Status badges (sidebar + header) and year actions menu
    expect((await screen.findAllByText('Filed')).length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Change status' })).toBeInTheDocument();
    // Calculation result, payments, deadlines and comparison sections
    expect(screen.getByText('Tax Breakdown — Canton SG 2024')).toBeInTheDocument();
    expect(screen.getByText('Instalment')).toBeInTheDocument();
    expect(screen.getByText('File return')).toBeInTheDocument();
    expect(screen.getByText('Monthly Payments')).toBeInTheDocument();
    expect(screen.getByText('Year Comparison')).toBeInTheDocument();
  });

  it('shows the empty hint and the calculator for a year without a record', async () => {
    vi.mocked(taxApi.getYears).mockResolvedValue([]);
    vi.mocked(taxApi.getYear).mockRejectedValue(new Error('Tax year not found'));
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);

    renderTaxPage('/tax/2026');

    expect(
      await screen.findByText(/No data for 2026 yet — fill in the form/),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Calculate for 2026/ })).toBeInTheDocument();
    expect(screen.queryByText('Monthly Payments')).not.toBeInTheDocument();
  });

  it('falls back to the current year for an invalid year param', async () => {
    const currentYear = new Date().getFullYear();
    vi.mocked(taxApi.getYears).mockResolvedValue([]);
    vi.mocked(taxApi.getYear).mockRejectedValue(new Error('not found'));
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);

    renderTaxPage('/tax/1999');

    expect(
      await screen.findByRole('heading', { name: `Tax Year ${currentYear}` }),
    ).toBeInTheDocument();
    expect(taxApi.getYear).toHaveBeenCalledWith('test-token', currentYear);
  });

  it('lists the years from the server in the sidebar', async () => {
    const currentYear = new Date().getFullYear();
    vi.mocked(taxApi.getYears).mockResolvedValue([
      taxYearSummary({ year: currentYear - 1, status: 'PAID' }),
    ]);
    vi.mocked(taxApi.getYear).mockRejectedValue(new Error('not found'));
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);

    renderTaxPage('/tax');

    // Sidebar appears twice (desktop + mobile strip)
    expect((await screen.findAllByText(String(currentYear - 1))).length).toBeGreaterThan(0);
    expect(screen.getAllByText(String(currentYear + 1)).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Planned').length).toBeGreaterThan(0);
  });
});
