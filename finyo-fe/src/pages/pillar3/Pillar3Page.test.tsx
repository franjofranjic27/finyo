import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Pillar3Page } from './Pillar3Page';
import { pillar3Api } from '@/api/pillar3';
import { profileApi } from '@/api/profile';
import { emptyUserProfile } from '@/test/fixtures/profile';
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
    getScenarios: vi.fn(),
    compare: vi.fn(),
    createScenario: vi.fn(),
    setDefaultScenario: vi.fn(),
    deleteScenario: vi.fn(),
  },
}));

vi.mock('@/api/profile', () => ({
  PROFILE_QUERY_KEY: ['profile'],
  profileApi: {
    get: vi.fn(),
  },
}));

beforeEach(() => {
  vi.mocked(pillar3Api.getProducts).mockResolvedValue([]);
  vi.mocked(pillar3Api.getScenarios).mockResolvedValue([]);
  vi.mocked(profileApi.get).mockResolvedValue(emptyUserProfile());
});

describe('Pillar3Page', () => {
  it('renders all three tabs with the calculator as the default', () => {
    renderWithProviders(<Pillar3Page />);

    expect(screen.getByRole('tab', { name: 'Calculator' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Product Comparison' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Scenarios' })).toBeInTheDocument();
    // Calculator content is visible by default, the compare form is not.
    expect(screen.getAllByText('Pillar 3a Calculator').length).toBeGreaterThan(0);
    expect(screen.queryByText('Comparison Parameters')).not.toBeInTheDocument();
  });

  it('switches to the product comparison tab', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Pillar3Page />);

    await user.click(screen.getByRole('tab', { name: 'Product Comparison' }));

    expect(await screen.findByText('Comparison Parameters')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Calculate/ })).not.toBeInTheDocument();
  });

  it('switches to the scenarios tab', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Pillar3Page />);

    await user.click(screen.getByRole('tab', { name: 'Scenarios' }));

    expect(await screen.findByText(/No scenarios saved yet/)).toBeInTheDocument();
  });
});
