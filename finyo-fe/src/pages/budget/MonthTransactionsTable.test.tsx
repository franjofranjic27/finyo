import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MonthTransactionsTable } from './MonthTransactionsTable';
import { categoriesApi } from '@/api/categories';
import { categoryRulesApi } from '@/api/categoryRules';
import { transactionsApi } from '@/api/transactions';
import { renderWithProviders } from '@/test/test-utils';
import { transaction, transactionPage } from '@/test/fixtures/transactions';
import type { Category } from '@/types';

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

vi.mock('@/api/transactions', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/transactions')>();
  return {
    ...actual,
    transactionsApi: {
      getAll: vi.fn(),
      update: vi.fn(),
      commitImport: vi.fn(),
    },
  };
});

vi.mock('@/api/categories', () => ({
  categoriesApi: { getAll: vi.fn() },
}));

vi.mock('@/api/categoryRules', () => ({
  categoryRulesApi: {
    getAll: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

const categories: Category[] = [
  { id: 'c1', name: 'Lebensmittel', type: 'EXPENSE' },
  { id: 'c2', name: 'Lohn', type: 'INCOME' },
];

const FROM = '2026-07-01';
const TO = '2026-07-31';

describe('MonthTransactionsTable', () => {
  beforeEach(() => {
    vi.mocked(categoriesApi.getAll).mockResolvedValue(categories);
  });

  it('updates the category with the full transaction payload', async () => {
    const tx = transaction({ id: 't1' });
    vi.mocked(transactionsApi.getAll).mockResolvedValue(transactionPage([tx]));
    vi.mocked(transactionsApi.update).mockResolvedValue({ ...tx, categoryId: 'c1' });
    const user = userEvent.setup();
    renderWithProviders(
      <MonthTransactionsTable from={FROM} to={TO} onImportClick={vi.fn()} />,
    );

    expect(await screen.findByText('MIGROS ZUERICH')).toBeInTheDocument();
    expect(transactionsApi.getAll).toHaveBeenCalledWith('test-token', {
      from: FROM,
      to: TO,
      page: 0,
      size: 50,
    });

    await user.click(screen.getByRole('combobox', { name: 'Category: MIGROS ZUERICH' }));
    await user.click(await screen.findByRole('option', { name: 'Lebensmittel' }));

    await waitFor(() =>
      expect(transactionsApi.update).toHaveBeenCalledWith('test-token', 't1', {
        amount: -42.5,
        currency: 'CHF',
        date: '2026-07-03',
        description: 'MIGROS ZUERICH',
        categoryId: 'c1',
        accountId: 'a1',
      }),
    );
  });

  it('offers to remember the change as a rule and creates it', async () => {
    const tx = transaction({ id: 't1' });
    vi.mocked(transactionsApi.getAll).mockResolvedValue(transactionPage([tx]));
    vi.mocked(transactionsApi.update).mockResolvedValue({ ...tx, categoryId: 'c1' });
    vi.mocked(categoryRulesApi.create).mockResolvedValue({
      id: 'r1',
      keyword: 'MIGROS ZUERICH',
      categoryId: 'c1',
      categoryName: 'Lebensmittel',
      categoryColor: null,
    });
    const user = userEvent.setup();
    renderWithProviders(
      <MonthTransactionsTable from={FROM} to={TO} onImportClick={vi.fn()} />,
    );

    await user.click(
      await screen.findByRole('combobox', { name: 'Category: MIGROS ZUERICH' }),
    );
    await user.click(await screen.findByRole('option', { name: 'Lebensmittel' }));

    await user.click(await screen.findByRole('button', { name: 'Remember as rule?' }));

    const keywordInput = await screen.findByLabelText('Keyword');
    expect(keywordInput).toHaveValue('MIGROS ZUERICH');
    await user.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() =>
      expect(categoryRulesApi.create).toHaveBeenCalledWith('test-token', {
        keyword: 'MIGROS ZUERICH',
        categoryId: 'c1',
      }),
    );
  });

  it('shows the empty state with an import call to action', async () => {
    vi.mocked(transactionsApi.getAll).mockResolvedValue(transactionPage([]));
    const onImportClick = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <MonthTransactionsTable from={FROM} to={TO} onImportClick={onImportClick} />,
    );

    expect(await screen.findByText('No transactions in this month')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Import bank statement' }));
    expect(onImportClick).toHaveBeenCalled();
  });
});
