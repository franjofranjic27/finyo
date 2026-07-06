import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Sidebar } from './Sidebar';
import { renderWithProviders } from '@/test/test-utils';

describe('Sidebar', () => {
  it('renders all navigation links plus settings', () => {
    renderWithProviders(<Sidebar />);

    for (const label of ['Dashboard', 'Investments', 'Taxes', 'Pillar 3a', 'Insurance', 'Settings']) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument();
    }
    expect(screen.getByText('finyo')).toBeInTheDocument();
  });

  it('hides labels and the logo when collapsed', () => {
    renderWithProviders(<Sidebar collapsed onToggleCollapse={() => {}} />);

    expect(screen.queryByText('finyo')).not.toBeInTheDocument();
    expect(screen.queryByText('Dashboard')).not.toBeInTheDocument();
    // Links stay reachable via their title attribute.
    expect(screen.getByTitle('Dashboard')).toBeInTheDocument();
  });

  it('invokes the collapse toggle', async () => {
    const onToggleCollapse = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<Sidebar onToggleCollapse={onToggleCollapse} />);

    await user.click(screen.getByRole('button', { name: 'Collapse sidebar' }));

    expect(onToggleCollapse).toHaveBeenCalledTimes(1);
  });

  it('shows no collapse button without a toggle handler (mobile sheet)', () => {
    renderWithProviders(<Sidebar />);

    expect(screen.queryByRole('button', { name: 'Collapse sidebar' })).not.toBeInTheDocument();
  });

  it('closes the mobile sheet when a link is clicked', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<Sidebar onClose={onClose} />);

    await user.click(screen.getByRole('link', { name: 'Taxes' }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
