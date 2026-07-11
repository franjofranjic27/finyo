import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Pillar3Page } from './Pillar3Page';
import { pillar3Api } from '@/api/pillar3';
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
  taxApi: { calculatePillar3: vi.fn() },
}));

vi.mock('@/api/pillar3', () => ({
  pillar3Api: {
    getProducts: vi.fn(),
    compare: vi.fn(),
  },
}));

describe('Pillar3Page', () => {
  it('renders both tabs with the calculator as the default', () => {
    renderWithProviders(<Pillar3Page />);

    expect(screen.getByRole('tab', { name: 'Calculator' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Product Comparison' })).toBeInTheDocument();
    // Calculator content is visible by default, the compare form is not.
    expect(screen.getAllByText('Pillar 3a Calculator').length).toBeGreaterThan(0);
    expect(screen.queryByText('Comparison Parameters')).not.toBeInTheDocument();
  });

  it('switches to the product comparison tab', async () => {
    vi.mocked(pillar3Api.getProducts).mockResolvedValue([]);
    const user = userEvent.setup();
    renderWithProviders(<Pillar3Page />);

    await user.click(screen.getByRole('tab', { name: 'Product Comparison' }));

    expect(await screen.findByText('Comparison Parameters')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Calculate/ })).not.toBeInTheDocument();
  });
});
