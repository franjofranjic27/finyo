import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AccountDialog } from './AccountDialog';
import { accountsApi } from '@/api/accounts';
import { renderWithProviders } from '@/test/test-utils';
import { bankAccount } from '@/test/fixtures/accounts';

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
      create: vi.fn(),
      update: vi.fn(),
    },
  };
});

describe('AccountDialog', () => {
  beforeEach(() => {
    vi.mocked(accountsApi.create).mockResolvedValue(bankAccount({ id: 'a1' }));
    vi.mocked(accountsApi.update).mockResolvedValue(bankAccount({ id: 'a1' }));
  });

  it('disables the submit until a name is entered', () => {
    renderWithProviders(<AccountDialog account={null} onClose={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled();
  });

  it('creates an account with the defaults and a normalized IBAN', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const { queryClient } = renderWithProviders(
      <AccountDialog account={null} onClose={onClose} />,
    );
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.type(screen.getByLabelText('Account name'), 'Neues Konto');
    await user.type(screen.getByLabelText('IBAN'), 'ch93 0076 2011 6238 5295 7');
    await user.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() =>
      expect(accountsApi.create).toHaveBeenCalledWith('test-token', {
        name: 'Neues Konto',
        type: 'CHECKING',
        currency: 'CHF',
        initialBalance: '0',
        iban: 'CH9300762011623852957',
        bic: undefined,
        contractNumber: undefined,
        feeNote: undefined,
        scope: 'PRIVATE',
        toClose: false,
      }),
    );
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts'] }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['cards'] });
    expect(onClose).toHaveBeenCalled();
  });

  it('sends the chosen scope and the to-close flag', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AccountDialog account={null} onClose={vi.fn()} />);

    await user.type(screen.getByLabelText('Account name'), 'Kontokorrent');
    await user.click(screen.getByRole('combobox', { name: 'Scope' }));
    await user.click(await screen.findByRole('option', { name: 'Business' }));
    await user.click(screen.getByLabelText('To close'));
    await user.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() =>
      expect(accountsApi.create).toHaveBeenCalledWith(
        'test-token',
        expect.objectContaining({ scope: 'BUSINESS', toClose: true }),
      ),
    );
  });

  it('keeps the stored initial balance when editing', async () => {
    const user = userEvent.setup();
    const account = bankAccount({ id: 'a1', initialBalance: '250.00' });
    renderWithProviders(<AccountDialog account={account} onClose={vi.fn()} />);

    expect(screen.getByLabelText('Account name')).toHaveValue('Privatkonto');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(accountsApi.update).toHaveBeenCalledWith(
        'test-token',
        'a1',
        expect.objectContaining({ name: 'Privatkonto', initialBalance: '250.00' }),
      ),
    );
  });

  it('shows the server-side IBAN validation error inline', async () => {
    vi.mocked(accountsApi.create).mockRejectedValue(new Error('IBAN checksum invalid'));
    const user = userEvent.setup();
    renderWithProviders(<AccountDialog account={null} onClose={vi.fn()} />);

    await user.type(screen.getByLabelText('Account name'), 'Neues Konto');
    await user.click(screen.getByRole('button', { name: 'Add' }));

    expect(await screen.findByText('IBAN checksum invalid')).toBeInTheDocument();
  });
});
