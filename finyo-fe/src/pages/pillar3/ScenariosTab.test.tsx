import { describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import { ScenariosTab } from './ScenariosTab';
import { pillar3Api } from '@/api/pillar3';
import {
  pillar3Product,
  pillar3Result,
  pillar3Scenario,
  pillar3ScenarioInputs,
} from '@/test/fixtures/pillar3';
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

vi.mock('@/api/pillar3', () => ({
  pillar3Api: {
    getScenarios: vi.fn(),
    setDefaultScenario: vi.fn(),
    deleteScenario: vi.fn(),
  },
}));

const scenarios = [
  pillar3Scenario({
    id: 's1',
    name: 'With product',
    isDefault: true,
    product: pillar3Product({ id: 'p1', name: 'Alpha Fund', provider: 'Alpha AG' }),
    effectiveReturnPercent: 3.2,
    inputs: pillar3ScenarioInputs({ annualContribution: 7258, productId: 'p1' }),
    calculation: pillar3Result({
      projectedBalanceAtRetirement: 350_000,
      annualTaxSaving: 1200,
      netValueAfterPayoutTax: 332_000,
    }),
  }),
  pillar3Scenario({
    id: 's2',
    name: 'Aggressive',
    product: null,
    effectiveReturnPercent: 6.5,
    inputs: pillar3ScenarioInputs({ annualContribution: 5000 }),
    calculation: pillar3Result({
      projectedBalanceAtRetirement: 500_000,
      annualTaxSaving: 0,
      netValueAfterPayoutTax: 470_000,
    }),
  }),
];

describe('ScenariosTab', () => {
  it('shows the empty state when no scenarios exist', async () => {
    vi.mocked(pillar3Api.getScenarios).mockResolvedValue([]);
    renderWithProviders(<ScenariosTab />);

    expect(await screen.findByText(/No scenarios saved yet/)).toBeInTheDocument();
  });

  it('renders one comparison row per scenario with the key figures', async () => {
    vi.mocked(pillar3Api.getScenarios).mockResolvedValue(scenarios);
    renderWithProviders(<ScenariosTab />);

    // The desktop table and the mobile list render the same data, so all
    // table assertions are scoped to the table element.
    const table = await screen.findByRole('table');
    const productRow = within(table).getByText('With product').closest('tr') as HTMLElement;
    expect(within(productRow).getByText('Alpha Fund')).toBeInTheDocument();
    expect(within(productRow).getByText("CHF 7'258.00")).toBeInTheDocument();
    expect(within(productRow).getByText("CHF 350'000.00")).toBeInTheDocument();
    expect(within(productRow).getByText("CHF 1'200.00")).toBeInTheDocument();
    expect(within(productRow).getByText("CHF 332'000.00")).toBeInTheDocument();

    const manualRow = within(table).getByText('Aggressive').closest('tr') as HTMLElement;
    expect(within(manualRow).getByText('6.5 %')).toBeInTheDocument();
    expect(within(manualRow).getByText("CHF 500'000.00")).toBeInTheDocument();
  });

  it('renders the mobile list with the same key figures per scenario', async () => {
    vi.mocked(pillar3Api.getScenarios).mockResolvedValue(scenarios);
    renderWithProviders(<ScenariosTab />);

    const list = await screen.findByRole('list');
    const productItem = within(list).getByText('With product').closest('li') as HTMLElement;
    expect(within(productItem).getByText('Alpha Fund')).toBeInTheDocument();
    expect(within(productItem).getByText("CHF 350'000.00")).toBeInTheDocument();
    expect(within(productItem).getByText(/Default/)).toBeInTheDocument();

    const manualItem = within(list).getByText('Aggressive').closest('li') as HTMLElement;
    expect(within(manualItem).getByText('6.5 %')).toBeInTheDocument();
    expect(within(manualItem).getByText('–')).toBeInTheDocument();
  });

  it('marks the default scenario with a badge and dashes a zero tax saving', async () => {
    vi.mocked(pillar3Api.getScenarios).mockResolvedValue(scenarios);
    renderWithProviders(<ScenariosTab />);

    const table = await screen.findByRole('table');
    const productRow = within(table).getByText('With product').closest('tr') as HTMLElement;
    expect(within(productRow).getByText(/Default/)).toBeInTheDocument();

    const manualRow = within(table).getByText('Aggressive').closest('tr') as HTMLElement;
    expect(within(manualRow).queryByText(/Default/)).not.toBeInTheDocument();
    expect(within(manualRow).getByText('–')).toBeInTheDocument();
  });

  it('offers a scenario actions menu per row and renders the growth chart card', async () => {
    vi.mocked(pillar3Api.getScenarios).mockResolvedValue(scenarios);
    renderWithProviders(<ScenariosTab />);

    // One menu per scenario in each of the two layout variants (table + mobile list).
    await screen.findByRole('table');
    expect(screen.getAllByRole('button', { name: 'Scenario actions' })).toHaveLength(4);
    expect(screen.getByText('Balance Growth')).toBeInTheDocument();
  });
});
