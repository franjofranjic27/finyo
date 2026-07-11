import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { RequireRole } from './RequireRole';
import { renderWithProviders } from '@/test/test-utils';

const authState = vi.hoisted(() => ({ roles: [] as string[] }));

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    user: { profile: {} },
    accessToken: 'test-token',
    roles: authState.roles,
    hasRole: (role: string) => authState.roles.includes(role),
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

describe('RequireRole', () => {
  beforeEach(() => {
    authState.roles = [];
  });

  it('renders the children when the role is present', () => {
    authState.roles = ['user', 'admin'];
    renderWithProviders(
      <RequireRole role="admin">
        <p>Admin content</p>
      </RequireRole>,
    );

    expect(screen.getByText('Admin content')).toBeInTheDocument();
    expect(screen.queryByText('Access denied')).not.toBeInTheDocument();
  });

  it('renders the access-denied screen when the role is missing', () => {
    authState.roles = ['user'];
    renderWithProviders(
      <RequireRole role="admin">
        <p>Admin content</p>
      </RequireRole>,
    );

    expect(screen.getByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
    expect(
      screen.getByText(/does not have the required role/),
    ).toBeInTheDocument();
    expect(screen.queryByText('Admin content')).not.toBeInTheDocument();
  });
});
