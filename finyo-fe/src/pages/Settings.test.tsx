import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Settings } from './Settings';
import { accountsApi } from '@/api/accounts';
import { apiRequest } from '@/api/client';
import { profileApi } from '@/api/profile';
import { renderWithProviders } from '@/test/test-utils';
import { userProfile } from '@/test/fixtures/profile';
import type { Account, Category } from '@/types';

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

vi.mock('@/api/accounts', () => ({
  accountsApi: {
    getAll: vi.fn(),
    getById: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('@/api/profile', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/profile')>()),
  profileApi: { get: vi.fn(), update: vi.fn(), updatePreferences: vi.fn() },
}));

vi.mock('@/api/client', () => ({ apiRequest: vi.fn() }));

const accounts: Account[] = [
  {
    id: 'a1',
    name: 'UBS Checking',
    type: 'CHECKING',
    currency: 'CHF',
    initialBalance: '1250.00',
    color: '#6366f1',
    iban: null,
    bic: null,
    contractNumber: null,
    feeNote: null,
    scope: 'PRIVATE',
    toClose: false,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
];

const categories: Category[] = [
  { id: 'c1', name: 'Groceries', type: 'EXPENSE', icon: '🛒' },
  { id: 'c2', name: 'Salary', type: 'INCOME' },
];

async function openAccountsTab() {
  const user = userEvent.setup();
  renderWithProviders(<Settings />);
  await user.click(screen.getByRole('tab', { name: 'Accounts' }));
  return user;
}

describe('Settings', () => {
  beforeEach(() => {
    vi.mocked(profileApi.get).mockResolvedValue(userProfile());
    vi.mocked(profileApi.update).mockResolvedValue(userProfile());
    vi.mocked(profileApi.updatePreferences).mockReset();
    vi.mocked(profileApi.updatePreferences).mockResolvedValue(userProfile());
  });

  it('shows the profile tab with the stored profile by default', async () => {
    renderWithProviders(<Settings />);

    expect(await screen.findByLabelText('Date of birth')).toHaveValue('1990-05-01');
    expect(screen.getByLabelText('First name')).toHaveValue('Anna');
    expect(screen.getByLabelText('Last name')).toHaveValue('Muster');
    expect(screen.getByLabelText('Street and no.')).toHaveValue('Musterstrasse 12');
    expect(screen.getByLabelText('Postal code')).toHaveValue('9000');
    expect(screen.getByLabelText('Phone')).toHaveValue('+41 79 123 45 67');
    expect(screen.getByText('Age: 36 · Years to retirement (65): 29 · Retirement in 2055'))
      .toBeInTheDocument();
    expect(profileApi.get).toHaveBeenCalledWith('test-token');
  });

  it('saves the profile and flashes a confirmation', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Settings />);
    const birthDate = await screen.findByLabelText('Date of birth');

    // Date inputs do not support per-key typing; set the value directly.
    fireEvent.change(birthDate, { target: { value: '1985-03-15' } });
    const municipality = screen.getByLabelText('Municipality of residence');
    await user.clear(municipality);
    await user.type(municipality, 'Gossau');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    // the PUT is a full replace — every master-data field AND the stored
    // preferences (language, theme, currency) must be resent, not dropped
    expect(profileApi.update).toHaveBeenCalledWith('test-token', {
      salutation: 'MS',
      firstName: 'Anna',
      lastName: 'Muster',
      birthDate: '1985-03-15',
      civilStatus: 'SINGLE',
      churchAffiliation: 'NONE',
      nationality: 'Schweiz',
      street: 'Musterstrasse 12',
      postalCode: '9000',
      city: 'St. Gallen',
      municipality: 'Gossau',
      cantonCode: 'SG',
      phone: '+41 79 123 45 67',
      preferredLanguage: 'en',
      theme: 'SYSTEM',
      defaultCurrency: 'CHF',
    });
    expect(await screen.findByText('Saved')).toBeInTheDocument();
  });

  it('disables saving while the first or last name is empty', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Settings />);
    const firstName = await screen.findByLabelText('First name');

    await user.clear(firstName);

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    expect(profileApi.update).not.toHaveBeenCalled();
  });

  it('patches theme changes from the profile tab without touching the master data', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Settings />);
    await screen.findByLabelText('Date of birth');

    await user.click(screen.getByRole('button', { name: 'Dark' }));

    expect(profileApi.updatePreferences).toHaveBeenCalledWith('test-token', { theme: 'DARK' });
    expect(profileApi.update).not.toHaveBeenCalled();
    expect(document.documentElement).toHaveClass('dark');
  });

  it('patches the default currency on change instead of a full-replace PUT', async () => {
    vi.mocked(profileApi.updatePreferences).mockResolvedValue(
      userProfile({ defaultCurrency: 'EUR' }),
    );
    const user = userEvent.setup();
    renderWithProviders(<Settings />);
    await screen.findByLabelText('Date of birth');

    await user.click(screen.getByRole('combobox', { name: 'Default currency' }));
    await user.click(await screen.findByRole('option', { name: 'EUR' }));

    expect(profileApi.updatePreferences).toHaveBeenCalledWith('test-token', {
      defaultCurrency: 'EUR',
    });
    expect(profileApi.update).not.toHaveBeenCalled();
  });

  it('shows the email from the auth account as read-only', async () => {
    renderWithProviders(<Settings />);

    const email = await screen.findByLabelText('Email');
    expect(email).toBeDisabled();
    expect(screen.getByText('The email address is managed via your account.'))
      .toBeInTheDocument();
  });

  it('shows the accounts tab with existing accounts', async () => {
    vi.mocked(accountsApi.getAll).mockResolvedValue(accounts);
    await openAccountsTab();

    expect(await screen.findByText('UBS Checking')).toBeInTheDocument();
    expect(screen.getByText(/CHF 1'250\.00 initial · CHF/)).toBeInTheDocument();
    expect(screen.getByText('Checking')).toBeInTheDocument();
  });

  it('shows an empty state without accounts', async () => {
    vi.mocked(accountsApi.getAll).mockResolvedValue([]);
    await openAccountsTab();

    expect(await screen.findByText('No accounts yet.')).toBeInTheDocument();
  });

  it('deletes an account after confirmation', async () => {
    vi.mocked(accountsApi.getAll).mockResolvedValue(accounts);
    vi.mocked(accountsApi.delete).mockResolvedValue(undefined);
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = await openAccountsTab();
    await screen.findByText('UBS Checking');

    const deleteButton = screen
      .getAllByRole('button')
      .find((button) => button.textContent?.trim() === '');
    await user.click(deleteButton!);

    expect(accountsApi.delete).toHaveBeenCalledWith('test-token', 'a1');
  });

  it('lists expense and income categories on the categories tab', async () => {
    vi.mocked(accountsApi.getAll).mockResolvedValue([]);
    vi.mocked(apiRequest).mockResolvedValue(categories);
    const user = userEvent.setup();
    renderWithProviders(<Settings />);

    await user.click(screen.getByRole('tab', { name: 'Categories' }));

    expect(await screen.findByText('Groceries')).toBeInTheDocument();
    expect(screen.getByText('Salary')).toBeInTheDocument();
    expect(screen.getByText('Expenses')).toBeInTheDocument();
    expect(screen.getByText('Income')).toBeInTheDocument();
    expect(apiRequest).toHaveBeenCalledWith('/categories', {}, 'test-token');
  });

  it('opens the add-account dialog and creates an account', async () => {
    vi.mocked(accountsApi.getAll).mockResolvedValue([]);
    vi.mocked(accountsApi.create).mockResolvedValue(accounts[0]);
    const user = await openAccountsTab();
    await screen.findByText('No accounts yet.');

    await user.click(screen.getByRole('button', { name: /Add Account/ }));
    await user.type(screen.getByPlaceholderText('UBS Checking'), 'Pillar 3a Account');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(accountsApi.create).toHaveBeenCalledWith('test-token', {
      name: 'Pillar 3a Account',
      type: 'CHECKING',
      currency: 'CHF',
      initialBalance: '0',
      color: undefined,
    });
  });
});
