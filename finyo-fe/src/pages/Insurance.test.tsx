import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Insurance } from './Insurance';
import { insuranceApi } from '@/api/insurance';
import type { InsuranceOverview } from '@/api/insurance';
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

vi.mock('@/api/insurance', () => ({
  insuranceApi: {
    getOverview: vi.fn(),
    updateStatus: vi.fn(),
  },
}));

function overview(overrides: Partial<InsuranceOverview> & { id: string }): InsuranceOverview {
  return {
    code: overrides.id,
    nameEn: `Name ${overrides.id}`,
    nameDe: `NameDe ${overrides.id}`,
    descriptionEn: `Description ${overrides.id}`,
    descriptionDe: `BeschreibungDe ${overrides.id}`,
    mandatory: false,
    recommended: false,
    sortOrder: 1,
    hasInsurance: false,
    ...overrides,
  };
}

const items: InsuranceOverview[] = [
  overview({
    id: 'health',
    nameEn: 'Health insurance',
    mandatory: true,
    hasInsurance: true,
    typicalCostMin: 3600,
    typicalCostMax: 6000,
    comparisonUrl: 'https://comparis.ch',
  }),
  overview({ id: 'liability', nameEn: 'Personal liability', recommended: true }),
  overview({ id: 'legal', nameEn: 'Legal protection' }),
];

describe('Insurance', () => {
  it('groups items into mandatory, recommended and optional sections', async () => {
    vi.mocked(insuranceApi.getOverview).mockResolvedValue(items);
    renderWithProviders(<Insurance />);

    expect(await screen.findByText('Health insurance')).toBeInTheDocument();
    expect(screen.getByText('Personal liability')).toBeInTheDocument();
    expect(screen.getByText('Legal protection')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Mandatory' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Recommended' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Optional' })).toBeInTheDocument();
  });

  it('shows the typical cost range and a comparison link', async () => {
    vi.mocked(insuranceApi.getOverview).mockResolvedValue(items);
    renderWithProviders(<Insurance />);

    expect(await screen.findByText(/CHF 3'600 – 6'000 \/ year/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Compare/ })).toHaveAttribute(
      'href',
      'https://comparis.ch',
    );
  });

  it('toggles the insurance status with the negated current value', async () => {
    vi.mocked(insuranceApi.getOverview).mockResolvedValue(items);
    vi.mocked(insuranceApi.updateStatus).mockResolvedValue(items[0]);
    const user = userEvent.setup();
    renderWithProviders(<Insurance />);

    await screen.findByText('Health insurance');
    const toggles = screen.getAllByRole('button', { name: 'I have this' });
    await user.click(toggles[0]);

    expect(insuranceApi.updateStatus).toHaveBeenCalledWith('test-token', 'health', false);
  });

  it('shows an empty state when no insurance types exist', async () => {
    vi.mocked(insuranceApi.getOverview).mockResolvedValue([]);
    renderWithProviders(<Insurance />);

    expect(await screen.findByText('No insurance types configured.')).toBeInTheDocument();
  });

  it('shows loading skeletons while the overview loads', () => {
    vi.mocked(insuranceApi.getOverview).mockReturnValue(new Promise(() => {}));
    renderWithProviders(<Insurance />);

    expect(screen.getByText('Insurance Overview')).toBeInTheDocument();
    expect(screen.queryByText('Health insurance')).not.toBeInTheDocument();
  });
});
