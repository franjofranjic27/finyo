import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ScenarioActionsMenu } from './ScenarioActionsMenu';
import { taxApi } from '@/api/tax';
import { taxScenario } from '@/test/fixtures/tax';
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
    setDefaultScenario: vi.fn(),
    deleteScenario: vi.fn(),
  },
}));

describe('ScenarioActionsMenu', () => {
  it('marks the scenario as default via the API', async () => {
    const scenario = taxScenario({ id: 's2', name: 'Without 3a' });
    vi.mocked(taxApi.setDefaultScenario).mockResolvedValue({ ...scenario, isDefault: true });
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioActionsMenu year={2025} scenario={scenario} onDeleted={vi.fn()} />,
    );

    await user.click(screen.getByRole('button', { name: 'Scenario actions' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Set as default' }));

    await waitFor(() =>
      expect(taxApi.setDefaultScenario).toHaveBeenCalledWith('test-token', 2025, 's2'),
    );
  });

  it('hides the set-default action for the default scenario', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioActionsMenu
        year={2025}
        scenario={taxScenario({ id: 's1', isDefault: true })}
        onDeleted={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Scenario actions' }));

    expect(await screen.findByRole('menuitem', { name: /Delete scenario/ })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Set as default' })).not.toBeInTheDocument();
  });

  it('deletes after confirmation and notifies the parent', async () => {
    vi.mocked(taxApi.deleteScenario).mockResolvedValue(undefined);
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const onDeleted = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioActionsMenu
        year={2025}
        scenario={taxScenario({ id: 's2', name: 'Without 3a' })}
        onDeleted={onDeleted}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Scenario actions' }));
    await user.click(await screen.findByRole('menuitem', { name: /Delete scenario/ }));

    await waitFor(() =>
      expect(taxApi.deleteScenario).toHaveBeenCalledWith('test-token', 2025, 's2'),
    );
    expect(onDeleted).toHaveBeenCalledTimes(1);
  });

  it('does not delete when the confirmation is declined', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioActionsMenu year={2025} scenario={taxScenario({ id: 's2' })} onDeleted={vi.fn()} />,
    );

    await user.click(screen.getByRole('button', { name: 'Scenario actions' }));
    await user.click(await screen.findByRole('menuitem', { name: /Delete scenario/ }));

    expect(taxApi.deleteScenario).not.toHaveBeenCalled();
  });
});
