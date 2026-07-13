import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { OnboardingPage } from './OnboardingPage';
import { profileApi } from '@/api/profile';
import { salaryApi } from '@/api/salary';
import { renderWithProviders } from '@/test/test-utils';
import { userProfile } from '@/test/fixtures/profile';
import { salary } from '@/test/fixtures/salary';

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

vi.mock('@/api/profile', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/profile')>()),
  profileApi: { get: vi.fn(), update: vi.fn() },
}));

vi.mock('@/api/salary', () => ({
  salaryApi: { get: vi.fn(), update: vi.fn() },
}));

function renderOnboarding() {
  return renderWithProviders(
    <Routes>
      <Route path="/onboarding" element={<OnboardingPage />} />
      <Route path="/dashboard" element={<div>dashboard page</div>} />
    </Routes>,
    { route: '/onboarding' },
  );
}

describe('OnboardingPage', () => {
  beforeEach(() => {
    vi.mocked(profileApi.update).mockResolvedValue(userProfile());
    vi.mocked(salaryApi.get).mockResolvedValue(salary());
    vi.mocked(salaryApi.update).mockResolvedValue(salary());
  });

  it('walks forward and backward through the three steps', async () => {
    const user = userEvent.setup();
    renderOnboarding();

    expect(screen.getByText('Welcome to finyo')).toBeInTheDocument();
    expect(screen.getByText('Personal')).toBeInTheDocument();
    expect(screen.getByText('Step 1 of 3')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Back' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByText('Income')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByText('Appearance')).toBeInTheDocument();
    expect(screen.getByText('Step 3 of 3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Finish' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Back' }));
    expect(screen.getByText('Income')).toBeInTheDocument();
  });

  it('shows the derived age line while typing the birth date', () => {
    renderOnboarding();

    fireEvent.change(screen.getByLabelText('Date of birth'), {
      target: { value: '1990-05-01' },
    });

    expect(screen.getByText(/Age: \d+ · Years to retirement \(65\): \d+/)).toBeInTheDocument();
  });

  it('skip completes the onboarding without further data and navigates to the dashboard', async () => {
    const user = userEvent.setup();
    renderOnboarding();

    await user.click(screen.getByRole('button', { name: 'Skip' }));

    await waitFor(() =>
      expect(profileApi.update).toHaveBeenCalledWith('test-token', { onboardingCompleted: true }),
    );
    expect(await screen.findByText('dashboard page')).toBeInTheDocument();
    expect(salaryApi.update).not.toHaveBeenCalled();
  });

  it('finish PUTs the profile and the salary with preserved deduction rates', async () => {
    const user = userEvent.setup();
    renderOnboarding();

    fireEvent.change(screen.getByLabelText('Date of birth'), {
      target: { value: '1990-05-01' },
    });
    await user.click(screen.getByRole('button', { name: 'Next' }));

    await user.click(screen.getByRole('button', { name: 'CHF per year' }));
    await user.type(screen.getByLabelText('Gross salary per year'), '78000');
    await user.click(screen.getByRole('checkbox', { name: '13th month salary' }));
    await user.click(screen.getByRole('button', { name: 'Next' }));

    await user.click(screen.getByRole('button', { name: 'Finish' }));

    await waitFor(() =>
      expect(profileApi.update).toHaveBeenCalledWith('test-token', {
        birthDate: '1990-05-01',
        civilStatus: 'SINGLE',
        churchAffiliation: 'NONE',
        preferredLanguage: 'en',
        theme: 'SYSTEM',
        onboardingCompleted: true,
      }),
    );
    await waitFor(() =>
      expect(salaryApi.update).toHaveBeenCalledWith('test-token', {
        grossMonthly: 6000,
        grossYearly: 78000,
        inputMode: 'YEARLY',
        thirteenthSalary: true,
        ahvPct: 5.3,
        alvPct: 1.1,
        nbuPct: 0.455,
        ktgPct: 0.17,
        pensionFixed: 85.35,
        otherFixed: 11,
      }),
    );
    expect(await screen.findByText('dashboard page')).toBeInTheDocument();
  });

  it('finish without a salary amount skips the salary update', async () => {
    const user = userEvent.setup();
    renderOnboarding();

    await user.click(screen.getByRole('button', { name: 'Next' }));
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await user.click(screen.getByRole('button', { name: 'Finish' }));

    await waitFor(() =>
      expect(profileApi.update).toHaveBeenCalledWith(
        'test-token',
        expect.objectContaining({ onboardingCompleted: true, birthDate: null }),
      ),
    );
    expect(salaryApi.get).not.toHaveBeenCalled();
    expect(salaryApi.update).not.toHaveBeenCalled();
    expect(await screen.findByText('dashboard page')).toBeInTheDocument();
  });

  it('applies the theme live from the preferences step', async () => {
    const user = userEvent.setup();
    renderOnboarding();

    await user.click(screen.getByRole('button', { name: 'Next' }));
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await user.click(screen.getByRole('button', { name: 'Dark' }));

    expect(document.documentElement).toHaveClass('dark');
    expect(localStorage.getItem('finyo-theme')).toBe('dark');
  });
});
