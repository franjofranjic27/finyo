import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { AccountsPage } from './AccountsPage';
import { accountsApi } from '@/api/accounts';
import { renderWithProviders } from '@/test/test-utils';
import { bankAccount, paymentCard } from '@/test/fixtures/accounts';

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

vi.mock('@/api/accounts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/accounts')>();
  return {
    ...actual,
    accountsApi: {
      getAll: vi.fn(),
      getById: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
      getCards: vi.fn(),
      createCard: vi.fn(),
      updateCard: vi.fn(),
      deleteCard: vi.fn(),
    },
  };
});

describe('AccountsPage', () => {
  it('renders bank accounts, cards and the privacy note from the queries', async () => {
    vi.mocked(accountsApi.getAll).mockResolvedValue([bankAccount({ id: 'a1' })]);
    vi.mocked(accountsApi.getCards).mockResolvedValue([paymentCard({ id: 'c1' })]);
    renderWithProviders(<AccountsPage />);

    expect(await screen.findByText('Bank accounts')).toBeInTheDocument();
    // Names appear in the mobile list and the desktop table (CSS-only visibility).
    expect(screen.getAllByText('Privatkonto').length).toBeGreaterThan(0);
    expect(screen.getByText('Cards')).toBeInTheDocument();
    expect(screen.getAllByText('Debit Mastercard').length).toBeGreaterThan(0);
    expect(
      screen.getByText(
        'IBANs and contract numbers are stored per user; card numbers (PAN), CVV or e-banking credentials are never captured.',
      ),
    ).toBeInTheDocument();
    expect(accountsApi.getAll).toHaveBeenCalledWith('test-token');
    expect(accountsApi.getCards).toHaveBeenCalledWith('test-token');
  });

  it('shows the empty states when no accounts or cards exist', async () => {
    vi.mocked(accountsApi.getAll).mockResolvedValue([]);
    vi.mocked(accountsApi.getCards).mockResolvedValue([]);
    renderWithProviders(<AccountsPage />);

    expect(await screen.findByText('No accounts yet')).toBeInTheDocument();
    expect(screen.getByText('No cards yet')).toBeInTheDocument();
  });

  it('shows an error message when the accounts cannot be loaded', async () => {
    vi.mocked(accountsApi.getAll).mockRejectedValue(new Error('boom'));
    vi.mocked(accountsApi.getCards).mockResolvedValue([]);
    renderWithProviders(<AccountsPage />);

    expect(await screen.findByText('Could not load accounts')).toBeInTheDocument();
    expect(screen.queryByText('Bank accounts')).not.toBeInTheDocument();
  });
});
