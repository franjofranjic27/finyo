import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TaxPage } from './TaxPage';
import { pillar3Api } from '@/api/pillar3';
import { salaryApi } from '@/api/salary';
import { taxApi } from '@/api/tax';
import { salary } from '@/test/fixtures/salary';
import {
  taxResult,
  taxScenario,
  taxYearDetail,
  taxYearSummary,
} from '@/test/fixtures/tax';
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
    getScenarios: vi.fn(),
    createScenario: vi.fn(),
    setDefaultScenario: vi.fn(),
    deleteScenario: vi.fn(),
  },
}));

vi.mock('@/api/pillar3', () => ({
  pillar3Api: {
    getScenarios: vi.fn(),
  },
}));

vi.mock('@/api/salary', () => ({
  salaryApi: {
    get: vi.fn(),
  },
}));

// Neutral defaults so the calculator's cross-module prefill stays inactive.
beforeEach(() => {
  vi.mocked(pillar3Api.getScenarios).mockResolvedValue([]);
  const stored = salary();
  vi.mocked(salaryApi.get).mockResolvedValue({
    ...stored,
    result: { ...stored.result, grossYearly: 0 },
  });
});

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
    vi.mocked(taxApi.getScenarios).mockResolvedValue([]);

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
    expect(screen.getByText('Scenarios')).toBeInTheDocument();
    expect(screen.getByText('Year Comparison')).toBeInTheDocument();
  });

  it('shows the empty hint and the calculator for a year without a record', async () => {
    vi.mocked(taxApi.getYears).mockResolvedValue([]);
    vi.mocked(taxApi.getYear).mockRejectedValue(new Error('Tax year not found'));
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(taxApi.getScenarios).mockResolvedValue([]);

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
    vi.mocked(taxApi.getScenarios).mockResolvedValue([]);

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
    vi.mocked(taxApi.getScenarios).mockResolvedValue([]);

    renderTaxPage('/tax');

    // Sidebar appears twice (desktop + mobile strip)
    expect((await screen.findAllByText(String(currentYear - 1))).length).toBeGreaterThan(0);
    expect(screen.getAllByText(String(currentYear + 1)).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Planned').length).toBeGreaterThan(0);
  });

  it('switches the results to the selected scenario and back on deselect', async () => {
    vi.mocked(taxApi.getYears).mockResolvedValue([]);
    vi.mocked(taxApi.getYear).mockResolvedValue(
      taxYearDetail({ year: 2024, calculation: taxResult({ taxYear: 2024, grandTotal: 12_000 }) }),
    );
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(taxApi.getScenarios).mockResolvedValue([
      taxScenario({
        id: 's1',
        name: 'Status quo',
        isDefault: true,
        calculation: taxResult({ taxYear: 2024, grandTotal: 17_842 }),
      }),
      taxScenario({
        id: 's2',
        name: 'Without 3a',
        calculation: taxResult({ taxYear: 2024, grandTotal: 19_732 }),
      }),
    ]);
    const user = userEvent.setup();

    renderTaxPage('/tax/2024');

    expect(await screen.findByText('Tax Breakdown — Canton SG 2024')).toBeInTheDocument();

    await user.click(await screen.findByRole('button', { name: /Without 3a/ }));

    // Scenario figures, title suffix and the delta badge against the default
    expect(
      await screen.findByText('Tax Breakdown — Canton SG 2024 · Without 3a'),
    ).toBeInTheDocument();
    expect(screen.getByText("CHF 19'732.00")).toBeInTheDocument();
    expect(screen.getByText("+CHF 1'890.00 vs. default")).toBeInTheDocument();

    // Clicking the active chip deselects and restores the year's own result
    await user.click(screen.getByRole('button', { name: /Without 3a/ }));
    expect(await screen.findByText('Tax Breakdown — Canton SG 2024')).toBeInTheDocument();
    expect(screen.getByText("CHF 12'000.00")).toBeInTheDocument();
  });

  it('clears the selection when the selected scenario is deleted', async () => {
    vi.mocked(taxApi.getYears).mockResolvedValue([]);
    vi.mocked(taxApi.getYear).mockResolvedValue(
      taxYearDetail({ year: 2024, calculation: taxResult({ taxYear: 2024 }) }),
    );
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(taxApi.getScenarios).mockResolvedValue([
      taxScenario({
        id: 's2',
        name: 'Without 3a',
        calculation: taxResult({ taxYear: 2024, grandTotal: 19_732 }),
      }),
    ]);
    vi.mocked(taxApi.deleteScenario).mockResolvedValue(undefined);
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();

    renderTaxPage('/tax/2024');

    await user.click(await screen.findByRole('button', { name: /Without 3a/ }));
    expect(
      await screen.findByText('Tax Breakdown — Canton SG 2024 · Without 3a'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Scenario actions' }));
    await user.click(await screen.findByRole('menuitem', { name: /Delete scenario/ }));

    await waitFor(() =>
      expect(taxApi.deleteScenario).toHaveBeenCalledWith('test-token', 2024, 's2'),
    );
    expect(await screen.findByText('Tax Breakdown — Canton SG 2024')).toBeInTheDocument();
  });

  it('clears the scenario selection after recalculating the year', async () => {
    const detail = taxYearDetail({
      year: 2024,
      calculation: taxResult({ taxYear: 2024, grandTotal: 12_000 }),
    });
    vi.mocked(taxApi.getYears).mockResolvedValue([]);
    vi.mocked(taxApi.getYear).mockResolvedValue(detail);
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(taxApi.getScenarios).mockResolvedValue([
      taxScenario({
        id: 's2',
        name: 'Without 3a',
        calculation: taxResult({ taxYear: 2024, grandTotal: 19_732 }),
      }),
    ]);
    vi.mocked(taxApi.upsertYear).mockResolvedValue(detail);
    const user = userEvent.setup();

    renderTaxPage('/tax/2024');

    await user.click(await screen.findByRole('button', { name: /Without 3a/ }));
    expect(
      await screen.findByText('Tax Breakdown — Canton SG 2024 · Without 3a'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Calculate for 2024/ }));

    expect(await screen.findByText('Tax Breakdown — Canton SG 2024')).toBeInTheDocument();
    expect(screen.getByText("CHF 12'000.00")).toBeInTheDocument();
  });

  it('opens the inline save panel via the new-scenario chip', async () => {
    vi.mocked(taxApi.getYears).mockResolvedValue([]);
    vi.mocked(taxApi.getYear).mockResolvedValue(
      taxYearDetail({ year: 2024, calculation: taxResult({ taxYear: 2024 }) }),
    );
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(taxApi.getScenarios).mockResolvedValue([]);
    const user = userEvent.setup();

    renderTaxPage('/tax/2024');

    await user.click(await screen.findByRole('button', { name: /New scenario/ }));

    expect(screen.getByLabelText('Name')).toBeInTheDocument();
    expect(screen.getByLabelText('Set as default scenario for 2024')).toBeInTheDocument();
  });

  it('persists the current form before opening the save panel', async () => {
    const detail = taxYearDetail({ year: 2024, calculation: taxResult({ taxYear: 2024 }) });
    vi.mocked(taxApi.getYears).mockResolvedValue([]);
    vi.mocked(taxApi.getYear).mockResolvedValue(detail);
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(taxApi.getScenarios).mockResolvedValue([]);
    vi.mocked(taxApi.upsertYear).mockResolvedValue(detail);
    const user = userEvent.setup();

    renderTaxPage('/tax/2024');

    await user.click(await screen.findByRole('button', { name: /Save scenario/ }));

    // The panel only opens after the form was persisted (edited values are
    // never silently skipped in the scenario snapshot).
    expect(await screen.findByLabelText('Name')).toBeInTheDocument();
    expect(taxApi.upsertYear).toHaveBeenCalledTimes(1);
  });

  it('keeps the save panel closed when persisting the form fails', async () => {
    vi.mocked(taxApi.getYears).mockResolvedValue([]);
    vi.mocked(taxApi.getYear).mockResolvedValue(
      taxYearDetail({ year: 2024, calculation: taxResult({ taxYear: 2024 }) }),
    );
    vi.mocked(taxApi.getCommunes).mockResolvedValue([]);
    vi.mocked(taxApi.getScenarios).mockResolvedValue([]);
    vi.mocked(taxApi.upsertYear).mockRejectedValue(new Error('No rate data for 2024'));
    const user = userEvent.setup();

    renderTaxPage('/tax/2024');

    await user.click(await screen.findByRole('button', { name: /Save scenario/ }));

    expect(await screen.findByText('No rate data for 2024')).toBeInTheDocument();
    expect(screen.queryByLabelText('Name')).not.toBeInTheDocument();
  });
});
