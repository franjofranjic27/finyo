import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CardDialog } from './CardDialog';
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
      createCard: vi.fn(),
      updateCard: vi.fn(),
    },
  };
});

const accounts = [
  bankAccount({ id: 'a1', name: 'Kontokorrent', scope: 'BUSINESS' }),
  bankAccount({ id: 'a2' }),
];

describe('CardDialog', () => {
  beforeEach(() => {
    vi.mocked(accountsApi.createCard).mockResolvedValue(paymentCard({ id: 'c1' }));
  });

  it('creates a card linked to the selected account', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const { queryClient } = renderWithProviders(
      <CardDialog card={null} accounts={accounts} onClose={onClose} />,
    );
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.type(screen.getByLabelText('Name'), 'Debit Mastercard');
    await user.type(screen.getByLabelText('Provider'), 'Mastercard');
    await user.click(screen.getByRole('combobox', { name: 'Linked account' }));
    await user.click(await screen.findByRole('option', { name: 'Kontokorrent' }));
    await user.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() =>
      expect(accountsApi.createCard).toHaveBeenCalledWith('test-token', {
        name: 'Debit Mastercard',
        provider: 'Mastercard',
        accountId: 'a1',
        currency: 'CHF',
        feeNote: undefined,
        scope: 'PRIVATE',
      }),
    );
    await waitFor(() => expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['cards'] }));
    expect(onClose).toHaveBeenCalled();
  });

  it('creates an unlinked card when no account is selected', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CardDialog card={null} accounts={accounts} onClose={vi.fn()} />);

    await user.type(screen.getByLabelText('Name'), 'Debit V PAY');
    await user.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() =>
      expect(accountsApi.createCard).toHaveBeenCalledWith(
        'test-token',
        expect.objectContaining({ name: 'Debit V PAY', accountId: undefined }),
      ),
    );
  });

  it('updates an existing card with its linked account preselected', async () => {
    vi.mocked(accountsApi.updateCard).mockResolvedValue(paymentCard({ id: 'c1' }));
    const user = userEvent.setup();
    const card = paymentCard({ id: 'c1', accountId: 'a1' });
    renderWithProviders(<CardDialog card={card} accounts={accounts} onClose={vi.fn()} />);

    expect(screen.getByLabelText('Name')).toHaveValue('Debit Mastercard');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(accountsApi.updateCard).toHaveBeenCalledWith(
        'test-token',
        'c1',
        expect.objectContaining({ accountId: 'a1', scope: 'BUSINESS' }),
      ),
    );
  });
});
