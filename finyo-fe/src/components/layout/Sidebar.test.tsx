import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Sidebar } from './Sidebar';
import { renderWithProviders } from '@/test/test-utils';

const authState = vi.hoisted(() => ({ isAdmin: false }));

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    user: { profile: {} },
    accessToken: 'test-token',
    roles: authState.isAdmin ? ['user', 'admin'] : ['user'],
    hasRole: (role: string) => role === 'admin' && authState.isAdmin,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

beforeEach(() => {
  authState.isAdmin = false;
});

describe('Sidebar', () => {
  it('renders all navigation links plus settings', () => {
    renderWithProviders(<Sidebar />);

    for (const label of ['Dashboard', 'Investments', 'Taxes', 'Pillar 3a', 'Insurance', 'Settings']) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument();
    }
    // The wordmark lives in the header now, not the sidebar.
    expect(screen.queryByText('finyo')).not.toBeInTheDocument();
  });

  it('hides labels when collapsed', () => {
    renderWithProviders(<Sidebar collapsed onToggleCollapse={() => {}} />);

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

  it('hides the admin group without the admin role', () => {
    renderWithProviders(<Sidebar />);

    expect(screen.queryByText('Admin')).not.toBeInTheDocument();
    expect(screen.queryByText('3a Products')).not.toBeInTheDocument();
  });

  it('reveals the admin sub-link when the admin group is expanded', async () => {
    authState.isAdmin = true;
    const user = userEvent.setup();
    renderWithProviders(<Sidebar />);

    // Closed by default outside /admin routes.
    expect(screen.queryByRole('link', { name: '3a Products' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Admin/ }));

    expect(screen.getByRole('link', { name: '3a Products' })).toHaveAttribute(
      'href',
      '/admin/pillar3-products',
    );
  });

  it('opens the admin group by default on an admin route', () => {
    authState.isAdmin = true;
    renderWithProviders(<Sidebar />, { route: '/admin/pillar3-products' });

    expect(screen.getByRole('link', { name: '3a Products' })).toBeInTheDocument();
  });

  it('keeps an icon-only admin link on the collapsed rail', () => {
    authState.isAdmin = true;
    renderWithProviders(<Sidebar collapsed onToggleCollapse={() => {}} />);

    // No group header on the rail, just the icon link with its tooltip title.
    expect(screen.queryByText('Admin')).not.toBeInTheDocument();
    const adminLink = screen.getByTitle('3a Products');
    expect(adminLink).toHaveAttribute('href', '/admin/pillar3-products');
    expect(adminLink).not.toHaveTextContent('3a Products');
  });
});
