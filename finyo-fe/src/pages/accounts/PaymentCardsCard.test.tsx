import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PaymentCardsCard } from './PaymentCardsCard';
import { accountsApi } from '@/api/accounts';
import type { PaymentCard } from '@/types';
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
      deleteCard: vi.fn(),
    },
  };
});

const cards = [
  paymentCard({ id: 'c1' }),
  paymentCard({
    id: 'c2',
    name: 'Debit V PAY',
    provider: 'Visa',
    accountId: null,
    accountName: null,
    feeNote: null,
    scope: 'PRIVATE',
  }),
];

function renderCards(items: PaymentCard[] = cards) {
  return renderWithProviders(
    <PaymentCardsCard cards={items} accounts={[bankAccount({ id: 'a1' })]} />,
  );
}

/**
 * The card renders a mobile list and a desktop table in parallel (CSS-only
 * visibility) — resolve the table row of a card through its name cell.
 */
function tableRowByText(text: string): HTMLElement {
  const row = screen
    .getAllByText(text)
    .map((element) => element.closest('tr'))
    .find((element) => element !== null);
  if (!row) throw new Error(`no table row containing “${text}”`);
  return row;
}

describe('PaymentCardsCard', () => {
  beforeEach(() => {
    vi.mocked(accountsApi.deleteCard).mockResolvedValue(undefined);
  });

  it('renders the card rows with scope label and linked account', () => {
    renderCards();

    const row = tableRowByText('Debit Mastercard');
    const cells = within(row).getAllByRole('cell');
    expect(within(cells[0]).getByText('Business')).toBeInTheDocument();
    expect(cells[1]).toHaveTextContent('Mastercard');
    expect(cells[2]).toHaveTextContent('Kontokorrent');
    expect(cells[3]).toHaveTextContent('CHF');
    expect(cells[4]).toHaveTextContent('CHF 50 / Jahr');
  });

  it('falls back to a dash when a card has no linked account', () => {
    renderCards();

    const row = tableRowByText('Debit V PAY');
    const cells = within(row).getAllByRole('cell');
    expect(cells[2]).toHaveTextContent('—');
  });

  it('deletes a card after confirmation and refreshes the cards query', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    const { queryClient } = renderCards();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.click(screen.getAllByRole('button', { name: 'Remove card' })[0]);

    expect(globalThis.confirm).toHaveBeenCalledWith('Remove card “Debit Mastercard”?');
    await waitFor(() => expect(accountsApi.deleteCard).toHaveBeenCalledWith('test-token', 'c1'));
    await waitFor(() => expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['cards'] }));
  });

  it('does not delete when the confirmation is declined', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderCards();

    await user.click(screen.getAllByRole('button', { name: 'Remove card' })[0]);

    expect(accountsApi.deleteCard).not.toHaveBeenCalled();
  });

  it('opens the create dialog from the header button', async () => {
    const user = userEvent.setup();
    renderCards();

    await user.click(screen.getByRole('button', { name: 'Add card' }));

    expect(screen.getByRole('heading', { name: 'Add card' })).toBeInTheDocument();
  });

  it('shows the empty state when there are no cards', () => {
    renderCards([]);

    expect(screen.getByText('No cards yet')).toBeInTheDocument();
  });
});
