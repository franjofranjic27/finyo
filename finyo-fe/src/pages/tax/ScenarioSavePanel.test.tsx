import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ScenarioSavePanel } from './ScenarioSavePanel';
import { taxApi } from '@/api/tax';
import { taxScenario, taxYearInputs } from '@/test/fixtures/tax';
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
    createScenario: vi.fn(),
    setDefaultScenario: vi.fn(),
  },
}));

const inputs = taxYearInputs();

describe('ScenarioSavePanel', () => {
  it('saves a plain scenario with the flat payload', async () => {
    const created = taxScenario({ id: 's1', name: 'Married' });
    vi.mocked(taxApi.createScenario).mockResolvedValue(created);
    const onSaved = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioSavePanel
        year={2025}
        inputs={inputs}
        hasDefault={false}
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );

    await user.type(screen.getByLabelText('Name'), 'Married');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(taxApi.createScenario).toHaveBeenCalledWith('test-token', 2025, {
        ...inputs,
        name: 'Married',
        isDefault: false,
      }),
    );
    expect(taxApi.setDefaultScenario).not.toHaveBeenCalled();
    expect(onSaved).toHaveBeenCalledWith(created);
  });

  it('creates directly as default when no default exists yet', async () => {
    vi.mocked(taxApi.createScenario).mockResolvedValue(
      taxScenario({ id: 's1', name: 'Status quo', isDefault: true }),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioSavePanel
        year={2025}
        inputs={inputs}
        hasDefault={false}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );

    await user.type(screen.getByLabelText('Name'), 'Status quo');
    await user.click(screen.getByLabelText('Set as default scenario for 2025'));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(taxApi.createScenario).toHaveBeenCalledWith('test-token', 2025, {
        ...inputs,
        name: 'Status quo',
        isDefault: true,
      }),
    );
    expect(taxApi.setDefaultScenario).not.toHaveBeenCalled();
  });

  it('creates non-default and switches afterwards when a default already exists', async () => {
    const created = taxScenario({ id: 's9', name: 'New default' });
    vi.mocked(taxApi.createScenario).mockResolvedValue(created);
    vi.mocked(taxApi.setDefaultScenario).mockResolvedValue({ ...created, isDefault: true });
    const onSaved = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioSavePanel
        year={2025}
        inputs={inputs}
        hasDefault
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );

    await user.type(screen.getByLabelText('Name'), 'New default');
    await user.click(screen.getByLabelText('Set as default scenario for 2025'));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(taxApi.createScenario).toHaveBeenCalledWith('test-token', 2025, {
        ...inputs,
        name: 'New default',
        isDefault: false,
      }),
    );
    await waitFor(() =>
      expect(taxApi.setDefaultScenario).toHaveBeenCalledWith('test-token', 2025, 's9'),
    );
    expect(onSaved).toHaveBeenCalledWith({ ...created, isDefault: true });
  });

  it('disables saving without a name and closes via cancel', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioSavePanel
        year={2025}
        inputs={inputs}
        hasDefault={false}
        onClose={onClose}
        onSaved={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('surfaces the backend error when saving fails', async () => {
    vi.mocked(taxApi.createScenario).mockRejectedValue(new Error('Name already taken'));
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioSavePanel
        year={2025}
        inputs={inputs}
        hasDefault={false}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );

    await user.type(screen.getByLabelText('Name'), 'Duplicate');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Name already taken')).toBeInTheDocument();
  });

  it('invalidates the scenario list even when the default switch fails after create', async () => {
    vi.mocked(taxApi.createScenario).mockResolvedValue(
      taxScenario({ id: 's9', name: 'New default' }),
    );
    vi.mocked(taxApi.setDefaultScenario).mockRejectedValue(new Error('Switch failed'));
    const onSaved = vi.fn();
    const user = userEvent.setup();
    const { queryClient } = renderWithProviders(
      <ScenarioSavePanel
        year={2025}
        inputs={inputs}
        hasDefault
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.type(screen.getByLabelText('Name'), 'New default');
    await user.click(screen.getByLabelText('Set as default scenario for 2025'));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    // The scenario was persisted by the create call: the list must refetch so
    // it shows up despite the error, and the error itself stays visible.
    expect(await screen.findByText('Switch failed')).toBeInTheDocument();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['tax', 'scenarios', '2025'] });
    expect(onSaved).not.toHaveBeenCalled();
  });
});
