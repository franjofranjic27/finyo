import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Settings } from './Settings';
import { accountsApi } from '@/api/accounts';
import { apiRequest } from '@/api/client';
import { renderWithProviders } from '@/test/test-utils';
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

describe('Settings', () => {
  it('shows the accounts tab with existing accounts by default', async () => {
    vi.mocked(accountsApi.getAll).mockResolvedValue(accounts);
    renderWithProviders(<Settings />);

    expect(await screen.findByText('UBS Checking')).toBeInTheDocument();
    expect(screen.getByText(/CHF 1'250\.00 initial · CHF/)).toBeInTheDocument();
    expect(screen.getByText('CHECKING')).toBeInTheDocument();
  });

  it('shows an empty state without accounts', async () => {
    vi.mocked(accountsApi.getAll).mockResolvedValue([]);
    renderWithProviders(<Settings />);

    expect(await screen.findByText('No accounts yet.')).toBeInTheDocument();
  });

  it('deletes an account after confirmation', async () => {
    vi.mocked(accountsApi.getAll).mockResolvedValue(accounts);
    vi.mocked(accountsApi.delete).mockResolvedValue(undefined);
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    renderWithProviders(<Settings />);
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
    const user = userEvent.setup();
    renderWithProviders(<Settings />);
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
