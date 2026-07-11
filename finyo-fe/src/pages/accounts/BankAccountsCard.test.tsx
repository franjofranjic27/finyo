import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BankAccountsCard } from './BankAccountsCard';
import { accountsApi } from '@/api/accounts';
import type { Account } from '@/types';
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

// Deliberately unordered input: grouping must impose PRIVATE → BUSINESS order.
const accounts = [
  bankAccount({
    id: 'a2',
    name: 'Kontokorrent',
    scope: 'BUSINESS',
    iban: 'CH3700781000000000305',
    bic: 'KBSGCH22',
    contractNumber: '1895560',
    feeNote: 'CHF 5 / Monat',
  }),
  bankAccount({ id: 'a1' }),
  bankAccount({
    id: 'a3',
    name: 'Crypto.com',
    type: 'OTHER',
    currency: 'EUR',
    iban: null,
    bic: null,
    contractNumber: null,
    toClose: true,
  }),
];

/** Installs a clipboard mock that wins over the one userEvent.setup() provides. */
function mockClipboard() {
  const writeText = vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(navigator, 'clipboard', {
    value: { writeText },
    configurable: true,
  });
  return writeText;
}

function renderCard(items: Account[] = accounts) {
  return renderWithProviders(<BankAccountsCard accounts={items} />);
}

describe('BankAccountsCard', () => {
  beforeEach(() => {
    vi.mocked(accountsApi.delete).mockResolvedValue(undefined);
  });

  it('groups accounts under scope subheaders with private first', () => {
    renderCard();

    const rowTexts = screen.getAllByRole('row').map((row) => row.textContent ?? '');
    const privateHeaderIndex = rowTexts.findIndex((text) => text === 'Private');
    const privatkontoIndex = rowTexts.findIndex((text) => text.includes('Privatkonto'));
    const businessHeaderIndex = rowTexts.findIndex((text) => text === 'Business');
    const kontokorrentIndex = rowTexts.findIndex((text) => text.includes('Kontokorrent'));

    expect(privateHeaderIndex).toBeGreaterThan(-1);
    expect(privateHeaderIndex).toBeLessThan(privatkontoIndex);
    expect(privatkontoIndex).toBeLessThan(businessHeaderIndex);
    expect(businessHeaderIndex).toBeLessThan(kontokorrentIndex);
    // Account type is shown below the name.
    expect(screen.getByText('Other')).toBeInTheDocument();
    expect(screen.getByText('CHF 5 / Monat')).toBeInTheDocument();
  });

  it('hides a scope group without accounts', () => {
    renderCard([bankAccount({ id: 'a1' })]);

    expect(screen.getByText('Private')).toBeInTheDocument();
    expect(screen.queryByText('Business')).not.toBeInTheDocument();
  });

  it('shows the IBAN formatted in groups of four', () => {
    renderCard();

    expect(screen.getByText(/CH93 0076 2011 6238 5295 7/)).toBeInTheDocument();
    expect(screen.getByText(/CH37 0078 1000 0000 0030 5/)).toBeInTheDocument();
  });

  it('copies the raw normalized IBAN and shows the copied feedback', async () => {
    const user = userEvent.setup();
    const writeText = mockClipboard();
    renderCard();

    // First copy button belongs to the private Privatkonto row.
    await user.click(screen.getAllByRole('button', { name: 'Copy IBAN' })[0]);

    expect(writeText).toHaveBeenCalledWith('CH9300762011623852957');
    expect(await screen.findByRole('button', { name: 'IBAN copied' })).toBeInTheDocument();
  });

  it('renders a dash instead of an IBAN chip when the IBAN is missing', () => {
    renderCard();

    const row = screen.getByText('Crypto.com').closest('tr') as HTMLElement;
    expect(within(row).queryByRole('button', { name: 'Copy IBAN' })).not.toBeInTheDocument();
    expect(within(row).getAllByText('—').length).toBeGreaterThan(0);
  });

  it('shows the amber status badge for accounts marked to close', () => {
    renderCard();

    const row = screen.getByText('Crypto.com').closest('tr') as HTMLElement;
    expect(within(row).getByText('To close')).toBeInTheDocument();
    // Accounts without the flag have no status badge.
    const privatkontoRow = screen.getByText('Privatkonto').closest('tr') as HTMLElement;
    expect(within(privatkontoRow).queryByText('To close')).not.toBeInTheDocument();
  });

  it('deletes an account after confirmation and refreshes accounts and cards', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    const { queryClient } = renderCard();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.click(screen.getAllByRole('button', { name: 'Remove account' })[0]);

    expect(globalThis.confirm).toHaveBeenCalledWith('Remove account “Privatkonto”?');
    await waitFor(() => expect(accountsApi.delete).toHaveBeenCalledWith('test-token', 'a1'));
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts'] }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['cards'] });
  });

  it('does not delete when the confirmation is declined', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderCard();

    await user.click(screen.getAllByRole('button', { name: 'Remove account' })[0]);

    expect(accountsApi.delete).not.toHaveBeenCalled();
  });

  it('opens the create dialog from the header button', async () => {
    const user = userEvent.setup();
    renderCard();

    await user.click(screen.getByRole('button', { name: 'Add account' }));

    expect(screen.getByRole('heading', { name: 'Add account' })).toBeInTheDocument();
  });

  it('opens the edit dialog prefilled with the account', async () => {
    const user = userEvent.setup();
    renderCard();

    await user.click(screen.getAllByRole('button', { name: 'Edit account' })[0]);

    expect(screen.getByRole('heading', { name: 'Edit account' })).toBeInTheDocument();
    expect(screen.getByLabelText('Account name')).toHaveValue('Privatkonto');
  });

  it('shows the empty state when there are no accounts', () => {
    renderCard([]);

    expect(screen.getByText('No accounts yet')).toBeInTheDocument();
  });
});
