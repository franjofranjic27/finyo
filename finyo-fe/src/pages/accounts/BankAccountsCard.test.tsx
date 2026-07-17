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
      importAccounts: vi.fn(),
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

/**
 * The card renders a mobile list and a desktop table in parallel (CSS-only
 * visibility) — resolve the table row of an account through its name cell.
 */
function tableRowByText(text: string): HTMLElement {
  const row = screen
    .getAllByText(text)
    .map((element) => element.closest('tr'))
    .find((element) => element !== null);
  if (!row) throw new Error(`no table row containing “${text}”`);
  return row;
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
    // Account type is shown below the name — in the table cell and the mobile row.
    expect(screen.getAllByText('Other').length).toBeGreaterThan(0);
    expect(screen.getByText('CHF 5 / Monat')).toBeInTheDocument();
  });

  it('hides a scope group without accounts', () => {
    renderCard([bankAccount({ id: 'a1' })]);

    expect(screen.getAllByText('Private').length).toBeGreaterThan(0);
    expect(screen.queryByText('Business')).not.toBeInTheDocument();
  });

  it('shows the IBAN formatted in groups of four', () => {
    renderCard();

    expect(screen.getAllByText(/CH93 0076 2011 6238 5295 7/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/CH37 0078 1000 0000 0030 5/).length).toBeGreaterThan(0);
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

    const row = tableRowByText('Crypto.com');
    expect(within(row).queryByRole('button', { name: 'Copy IBAN' })).not.toBeInTheDocument();
    expect(within(row).getAllByText('—').length).toBeGreaterThan(0);
  });

  it('shows the amber status badge for accounts marked to close', () => {
    renderCard();

    const row = tableRowByText('Crypto.com');
    expect(within(row).getByText('To close')).toBeInTheDocument();
    // Accounts without the flag have no status badge.
    const privatkontoRow = tableRowByText('Privatkonto');
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

  describe('CSV import', () => {
    function csvFile(content: string): File {
      return new File([content], 'accounts.csv', { type: 'text/csv' });
    }

    beforeEach(() => {
      vi.mocked(accountsApi.importAccounts).mockResolvedValue({
        created: 1,
        updated: 1,
        failed: 0,
        errors: [],
      });
    });

    it('imports the parsed rows, shows the result and refreshes accounts and cards', async () => {
      const user = userEvent.setup();
      const { queryClient } = renderCard();
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const file = csvFile([
        'Name;Typ;Bereich;Währung;IBAN;BIC;Vertragsnummer;Gebühren;Auflösen',
        'Privatkonto;Kontokorrent;Privat;CHF;CH93 0076 2011 6238 5295 7;RAIFCH22;81375-0005;CHF 5 / Monat;',
        'Altes Sparkonto;Sparkonto;;;;;;;ja',
      ].join('\n'));
      await user.upload(screen.getByLabelText('Import CSV'), file);

      await waitFor(() =>
        expect(accountsApi.importAccounts).toHaveBeenCalledWith('test-token', [
          {
            name: 'Privatkonto',
            type: 'CHECKING',
            scope: 'PRIVATE',
            currency: 'CHF',
            iban: 'CH93 0076 2011 6238 5295 7',
            bic: 'RAIFCH22',
            contractNumber: '81375-0005',
            feeNote: 'CHF 5 / Monat',
          },
          { name: 'Altes Sparkonto', type: 'SAVINGS', currency: 'CHF', toClose: true },
        ]),
      );
      expect(await screen.findByText('1 created, 1 updated, 0 failed')).toBeInTheDocument();
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['cards'] });
    });

    it('lists backend row errors from the bulk result', async () => {
      vi.mocked(accountsApi.importAccounts).mockResolvedValue({
        created: 1,
        updated: 0,
        failed: 1,
        errors: ['row 2: invalid IBAN checksum'],
      });
      const user = userEvent.setup();
      renderCard();

      await user.upload(
        screen.getByLabelText('Import CSV'),
        csvFile('Privatkonto;Kontokorrent\nSparkonto;Sparkonto;;;CH00 0000'),
      );

      expect(await screen.findByText('1 created, 0 updated, 1 failed')).toBeInTheDocument();
      expect(screen.getByText('row 2: invalid IBAN checksum')).toBeInTheDocument();
    });

    it('shows the no-rows message and skips the import when nothing parses', async () => {
      const user = userEvent.setup();
      renderCard();

      await user.upload(
        screen.getByLabelText('Import CSV'),
        csvFile(';Sparkonto\nOhne Typ;Wertschriften'),
      );

      expect(
        await screen.findByText('No valid rows found in the CSV file.'),
      ).toBeInTheDocument();
      expect(accountsApi.importAccounts).not.toHaveBeenCalled();
    });
  });
});
