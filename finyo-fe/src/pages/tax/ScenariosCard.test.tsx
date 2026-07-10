import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ScenariosCard } from './ScenariosCard';
import { taxApi } from '@/api/tax';
import { taxResult, taxScenario, taxYearInputs } from '@/test/fixtures/tax';
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
    getScenarios: vi.fn(),
    createScenario: vi.fn(),
    setDefaultScenario: vi.fn(),
    deleteScenario: vi.fn(),
  },
}));

const inputs = taxYearInputs();

const scenarios = [
  taxScenario({
    id: 's1',
    name: 'With max 3a',
    isDefault: true,
    calculation: taxResult({ totalIncomeTax: 10_500, grandTotal: 11_000 }),
  }),
  taxScenario({ id: 's2', name: 'No deductions', calculation: null }),
];

describe('ScenariosCard', () => {
  it('lists scenarios with default badge and key figures', async () => {
    vi.mocked(taxApi.getScenarios).mockResolvedValue(scenarios);
    renderWithProviders(<ScenariosCard year={2025} inputs={inputs} />);

    expect(await screen.findByText('With max 3a')).toBeInTheDocument();
    expect(screen.getByText('No deductions')).toBeInTheDocument();
    expect(screen.getByText('Default')).toBeInTheDocument();
    expect(screen.getByText(/CHF 10'500\.00/)).toBeInTheDocument();
    expect(screen.getByText(/CHF 11'000\.00/)).toBeInTheDocument();
    // Only the non-default scenario offers "set as default"
    expect(screen.getAllByRole('button', { name: 'Set as default' })).toHaveLength(1);
  });

  it('shows the empty state when no scenarios exist', async () => {
    vi.mocked(taxApi.getScenarios).mockResolvedValue([]);
    renderWithProviders(<ScenariosCard year={2025} inputs={inputs} />);

    expect(await screen.findByText('No scenarios saved yet')).toBeInTheDocument();
  });

  it('disables saving when the year has no inputs yet', async () => {
    vi.mocked(taxApi.getScenarios).mockResolvedValue([]);
    renderWithProviders(<ScenariosCard year={2025} inputs={null} />);

    expect(await screen.findByRole('button', { name: /Save scenario/ })).toBeDisabled();
  });

  it('saves a scenario with the current year inputs through the dialog', async () => {
    vi.mocked(taxApi.getScenarios).mockResolvedValue([]);
    vi.mocked(taxApi.createScenario).mockResolvedValue(
      taxScenario({ id: 's3', name: 'Married', isDefault: true }),
    );
    const user = userEvent.setup();
    renderWithProviders(<ScenariosCard year={2025} inputs={inputs} />);

    await user.click(screen.getByRole('button', { name: /Save scenario/ }));
    await user.type(screen.getByLabelText('Name'), 'Married');
    await user.click(screen.getByLabelText('Save as default'));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(taxApi.createScenario).toHaveBeenCalledWith('test-token', 2025, {
        ...inputs,
        name: 'Married',
        isDefault: true,
      }),
    );
  });

  it('surfaces the backend error when saving fails', async () => {
    vi.mocked(taxApi.getScenarios).mockResolvedValue([]);
    vi.mocked(taxApi.createScenario).mockRejectedValue(
      new Error('A default scenario already exists for 2025'),
    );
    const user = userEvent.setup();
    renderWithProviders(<ScenariosCard year={2025} inputs={inputs} />);

    await user.click(screen.getByRole('button', { name: /Save scenario/ }));
    await user.type(screen.getByLabelText('Name'), 'Duplicate default');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(
      await screen.findByText('A default scenario already exists for 2025'),
    ).toBeInTheDocument();
  });

  it('marks a scenario as default', async () => {
    vi.mocked(taxApi.getScenarios).mockResolvedValue(scenarios);
    vi.mocked(taxApi.setDefaultScenario).mockResolvedValue({
      ...scenarios[1],
      isDefault: true,
    });
    const user = userEvent.setup();
    renderWithProviders(<ScenariosCard year={2025} inputs={inputs} />);

    await user.click(await screen.findByRole('button', { name: 'Set as default' }));

    await waitFor(() =>
      expect(taxApi.setDefaultScenario).toHaveBeenCalledWith('test-token', 2025, 's2'),
    );
  });

  it('deletes a scenario after confirmation', async () => {
    vi.mocked(taxApi.getScenarios).mockResolvedValue(scenarios);
    vi.mocked(taxApi.deleteScenario).mockResolvedValue(undefined);
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    renderWithProviders(<ScenariosCard year={2025} inputs={inputs} />);

    const deleteButtons = await screen.findAllByRole('button', { name: 'Delete scenario' });
    await user.click(deleteButtons[0]);

    await waitFor(() =>
      expect(taxApi.deleteScenario).toHaveBeenCalledWith('test-token', 2025, 's1'),
    );
  });
});
