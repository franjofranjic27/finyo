import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ScenarioInlineRename } from './ScenarioInlineRename';
import { renderWithProviders } from '@/test/test-utils';

function renderRename(overrides: { isDefault?: boolean; onRename?: (name: string) => Promise<unknown> } = {}) {
  const onRename = overrides.onRename ?? vi.fn().mockResolvedValue(undefined);
  const result = renderWithProviders(
    <ScenarioInlineRename
      name="Status quo"
      isDefault={overrides.isDefault ?? false}
      renameLabel="Rename scenario"
      queryKey={['tax', 'scenarios', '2025']}
      onRename={onRename}
    />,
  );
  return { ...result, onRename };
}

describe('ScenarioInlineRename', () => {
  it('shows the name and switches to the edit input via the pencil button', async () => {
    const user = userEvent.setup();
    renderRename();

    expect(screen.getByText('Status quo')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Rename scenario' }));

    expect(screen.getByRole('textbox', { name: 'Rename scenario' })).toHaveValue('Status quo');
  });

  it('marks the default scenario with a star', () => {
    renderRename({ isDefault: true });
    expect(screen.getByText('★')).toBeInTheDocument();
  });

  it('confirms the rename with the trimmed name and invalidates the list', async () => {
    const user = userEvent.setup();
    const { onRename, queryClient } = renderRename();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.click(screen.getByRole('button', { name: 'Rename scenario' }));
    const input = screen.getByRole('textbox', { name: 'Rename scenario' });
    await user.clear(input);
    await user.type(input, '  Married 2026  ');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(onRename).toHaveBeenCalledWith('Married 2026'));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['tax', 'scenarios', '2025'] });
    // Back in display mode after the successful rename
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('disables the confirm button for a blank name', async () => {
    const user = userEvent.setup();
    renderRename();

    await user.click(screen.getByRole('button', { name: 'Rename scenario' }));
    await user.clear(screen.getByRole('textbox', { name: 'Rename scenario' }));

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });

  it('cancels the edit and restores the original name', async () => {
    const user = userEvent.setup();
    const { onRename } = renderRename();

    await user.click(screen.getByRole('button', { name: 'Rename scenario' }));
    const input = screen.getByRole('textbox', { name: 'Rename scenario' });
    await user.clear(input);
    await user.type(input, 'Discarded');
    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(onRename).not.toHaveBeenCalled();
    expect(screen.getByText('Status quo')).toBeInTheDocument();

    // Reopening starts from the persisted name, not the discarded draft
    await user.click(screen.getByRole('button', { name: 'Rename scenario' }));
    expect(screen.getByRole('textbox', { name: 'Rename scenario' })).toHaveValue('Status quo');
  });

  it('cancels the edit via Escape', async () => {
    const user = userEvent.setup();
    renderRename();

    await user.click(screen.getByRole('button', { name: 'Rename scenario' }));
    await user.keyboard('{Escape}');

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.getByText('Status quo')).toBeInTheDocument();
  });

  it('surfaces the backend error and stays in edit mode', async () => {
    const user = userEvent.setup();
    renderRename({ onRename: vi.fn().mockRejectedValue(new Error('Name already taken')) });

    await user.click(screen.getByRole('button', { name: 'Rename scenario' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Name already taken')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Rename scenario' })).toBeInTheDocument();
  });
});
