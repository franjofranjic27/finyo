import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { AppLayout } from './AppLayout';
import { renderWithProviders } from '@/test/test-utils';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    user: { profile: { preferred_username: 'anna' } },
    accessToken: 'test-token',
    roles: ['user'],
    hasRole: () => true,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

function renderLayout() {
  return renderWithProviders(
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/dashboard" element={<div>dashboard page body</div>} />
      </Route>
    </Routes>,
    { route: '/dashboard' },
  );
}

describe('AppLayout', () => {
  it('renders the routed page inside the layout with header and sidebar', () => {
    renderLayout();

    expect(screen.getByText('dashboard page body')).toBeInTheDocument();
    expect(screen.getByText('finyo')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
  });

  it('persists the collapsed state of the desktop sidebar', async () => {
    const user = userEvent.setup();
    renderLayout();

    await user.click(screen.getByRole('button', { name: 'Collapse sidebar' }));

    expect(localStorage.getItem('finyo.sidebar.collapsed')).toBe('true');
    expect(screen.getByRole('button', { name: 'Expand sidebar' })).toBeInTheDocument();
    expect(screen.queryByText('finyo')).not.toBeInTheDocument();
  });

  it('restores the collapsed state from localStorage', () => {
    localStorage.setItem('finyo.sidebar.collapsed', 'true');

    renderLayout();

    expect(screen.getByRole('button', { name: 'Expand sidebar' })).toBeInTheDocument();
  });
});
