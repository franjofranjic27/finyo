import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ScenarioActionsMenu } from './ScenarioActionsMenu';
import { pillar3Api } from '@/api/pillar3';
import { pillar3Scenario } from '@/test/fixtures/pillar3';
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
    setDefaultScenario: vi.fn(),
    deleteScenario: vi.fn(),
  },
}));

describe('ScenarioActionsMenu', () => {
  it('marks the scenario as default via the API', async () => {
    const scenario = pillar3Scenario({ id: 's2', name: 'Aggressive' });
    vi.mocked(pillar3Api.setDefaultScenario).mockResolvedValue({ ...scenario, isDefault: true });
    const user = userEvent.setup();
    renderWithProviders(<ScenarioActionsMenu scenario={scenario} onDeleted={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'Scenario actions' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Set as default scenario' }));

    await waitFor(() =>
      expect(pillar3Api.setDefaultScenario).toHaveBeenCalledWith('test-token', 's2'),
    );
  });

  it('hides the set-default action for the default scenario', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioActionsMenu
        scenario={pillar3Scenario({ id: 's1', isDefault: true })}
        onDeleted={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Scenario actions' }));

    expect(await screen.findByRole('menuitem', { name: /Delete scenario/ })).toBeInTheDocument();
    expect(
      screen.queryByRole('menuitem', { name: 'Set as default scenario' }),
    ).not.toBeInTheDocument();
  });

  it('deletes after confirmation and notifies the parent', async () => {
    vi.mocked(pillar3Api.deleteScenario).mockResolvedValue(undefined);
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const onDeleted = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioActionsMenu
        scenario={pillar3Scenario({ id: 's2', name: 'Aggressive' })}
        onDeleted={onDeleted}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Scenario actions' }));
    await user.click(await screen.findByRole('menuitem', { name: /Delete scenario/ }));

    await waitFor(() =>
      expect(pillar3Api.deleteScenario).toHaveBeenCalledWith('test-token', 's2'),
    );
    expect(onDeleted).toHaveBeenCalledTimes(1);
  });

  it('does not delete when the confirmation is declined', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderWithProviders(
      <ScenarioActionsMenu scenario={pillar3Scenario({ id: 's2' })} onDeleted={vi.fn()} />,
    );

    await user.click(screen.getByRole('button', { name: 'Scenario actions' }));
    await user.click(await screen.findByRole('menuitem', { name: /Delete scenario/ }));

    expect(pillar3Api.deleteScenario).not.toHaveBeenCalled();
  });
});
