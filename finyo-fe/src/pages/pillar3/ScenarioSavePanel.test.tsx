import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ScenarioSavePanel } from './ScenarioSavePanel';
import { pillar3Api } from '@/api/pillar3';
import { pillar3Scenario, pillar3ScenarioInputs } from '@/test/fixtures/pillar3';
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
    createScenario: vi.fn(),
    setDefaultScenario: vi.fn(),
  },
}));

const inputs = pillar3ScenarioInputs();

describe('ScenarioSavePanel', () => {
  it('saves a plain scenario with the flat payload', async () => {
    const created = pillar3Scenario({ id: 's1', name: 'With product' });
    vi.mocked(pillar3Api.createScenario).mockResolvedValue(created);
    const onSaved = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioSavePanel
        inputs={inputs}
        hasDefault={false}
        isFirst={false}
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );

    await user.type(screen.getByLabelText('Name'), 'With product');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(pillar3Api.createScenario).toHaveBeenCalledWith('test-token', {
        ...inputs,
        name: 'With product',
        isDefault: false,
      }),
    );
    expect(pillar3Api.setDefaultScenario).not.toHaveBeenCalled();
    expect(onSaved).toHaveBeenCalledWith(created);
  });

  it('creates directly as default when no default exists yet', async () => {
    vi.mocked(pillar3Api.createScenario).mockResolvedValue(
      pillar3Scenario({ id: 's1', name: 'Status quo', isDefault: true }),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioSavePanel
        inputs={inputs}
        hasDefault={false}
        isFirst={false}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );

    await user.type(screen.getByLabelText('Name'), 'Status quo');
    await user.click(screen.getByLabelText('Set as default scenario'));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(pillar3Api.createScenario).toHaveBeenCalledWith('test-token', {
        ...inputs,
        name: 'Status quo',
        isDefault: true,
      }),
    );
    expect(pillar3Api.setDefaultScenario).not.toHaveBeenCalled();
  });

  it('creates non-default and switches afterwards when a default already exists', async () => {
    const created = pillar3Scenario({ id: 's9', name: 'New default' });
    vi.mocked(pillar3Api.createScenario).mockResolvedValue(created);
    vi.mocked(pillar3Api.setDefaultScenario).mockResolvedValue({ ...created, isDefault: true });
    const onSaved = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioSavePanel
        inputs={inputs}
        hasDefault
        isFirst={false}
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );

    await user.type(screen.getByLabelText('Name'), 'New default');
    await user.click(screen.getByLabelText('Set as default scenario'));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(pillar3Api.createScenario).toHaveBeenCalledWith('test-token', {
        ...inputs,
        name: 'New default',
        isDefault: false,
      }),
    );
    await waitFor(() =>
      expect(pillar3Api.setDefaultScenario).toHaveBeenCalledWith('test-token', 's9'),
    );
    expect(onSaved).toHaveBeenCalledWith({ ...created, isDefault: true });
  });

  it('disables saving without a name and closes via cancel', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioSavePanel
        inputs={inputs}
        hasDefault={false}
        isFirst={false}
        onClose={onClose}
        onSaved={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('surfaces the backend error when saving fails', async () => {
    vi.mocked(pillar3Api.createScenario).mockRejectedValue(new Error('Name already taken'));
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioSavePanel
        inputs={inputs}
        hasDefault={false}
        isFirst={false}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );

    await user.type(screen.getByLabelText('Name'), 'Duplicate');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Name already taken')).toBeInTheDocument();
  });

  it('invalidates the scenario list even when the default switch fails after create', async () => {
    vi.mocked(pillar3Api.createScenario).mockResolvedValue(
      pillar3Scenario({ id: 's9', name: 'New default' }),
    );
    vi.mocked(pillar3Api.setDefaultScenario).mockRejectedValue(new Error('Switch failed'));
    const onSaved = vi.fn();
    const user = userEvent.setup();
    const { queryClient } = renderWithProviders(
      <ScenarioSavePanel
        inputs={inputs}
        hasDefault
        isFirst={false}
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.type(screen.getByLabelText('Name'), 'New default');
    await user.click(screen.getByLabelText('Set as default scenario'));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    // The scenario was persisted by the create call: the list must refetch so
    // it shows up despite the error, and the error itself stays visible.
    expect(await screen.findByText('Switch failed')).toBeInTheDocument();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['pillar3-scenarios'] });
    expect(onSaved).not.toHaveBeenCalled();
  });

  it('forces the first scenario to be the default and shows the hint', async () => {
    vi.mocked(pillar3Api.createScenario).mockResolvedValue(
      pillar3Scenario({ id: 's1', name: 'First', isDefault: true }),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioSavePanel
        inputs={inputs}
        hasDefault={false}
        isFirst
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );

    expect(screen.queryByLabelText('Set as default scenario')).not.toBeInTheDocument();
    expect(
      screen.getByText('The first scenario automatically becomes the default.'),
    ).toBeInTheDocument();

    await user.type(screen.getByLabelText('Name'), 'First');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(pillar3Api.createScenario).toHaveBeenCalledWith('test-token', {
        ...inputs,
        name: 'First',
        isDefault: true,
      }),
    );
    expect(pillar3Api.setDefaultScenario).not.toHaveBeenCalled();
  });
});
