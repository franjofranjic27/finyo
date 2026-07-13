import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CategoryRulesDialog } from './CategoryRulesDialog';
import { ApiError } from '@/api/client';
import { categoriesApi } from '@/api/categories';
import { categoryRulesApi } from '@/api/categoryRules';
import type { CategoryRule } from '@/api/categoryRules';
import { renderWithProviders } from '@/test/test-utils';
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
  { id: 'c2', name: 'Mobilität', type: 'EXPENSE' },
];

const rules: CategoryRule[] = [
  { id: 'r1', keyword: 'MIGROS', categoryId: 'c1', categoryName: 'Lebensmittel', categoryColor: '#22c55e' },
  { id: 'r2', keyword: 'SBB', categoryId: 'c2', categoryName: 'Mobilität', categoryColor: null },
];

describe('CategoryRulesDialog', () => {
  beforeEach(() => {
    vi.mocked(categoriesApi.getAll).mockResolvedValue(categories);
    vi.mocked(categoryRulesApi.getAll).mockResolvedValue(rules);
  });

  it('lists the existing rules with their categories', async () => {
    renderWithProviders(<CategoryRulesDialog onClose={vi.fn()} />);

    expect(await screen.findByText('MIGROS')).toBeInTheDocument();
    expect(screen.getByText('SBB')).toBeInTheDocument();
    expect(screen.getByText('Lebensmittel')).toBeInTheDocument();
    expect(screen.getByText('Mobilität')).toBeInTheDocument();
  });

  it('shows the empty state when there are no rules', async () => {
    vi.mocked(categoryRulesApi.getAll).mockResolvedValue([]);
    renderWithProviders(<CategoryRulesDialog onClose={vi.fn()} />);

    expect(await screen.findByText('No rules yet')).toBeInTheDocument();
  });

  it('adds a new rule from the inline form', async () => {
    vi.mocked(categoryRulesApi.create).mockResolvedValue({
      id: 'r3',
      keyword: 'COOP',
      categoryId: 'c1',
      categoryName: 'Lebensmittel',
      categoryColor: null,
    });
    const user = userEvent.setup();
    renderWithProviders(<CategoryRulesDialog onClose={vi.fn()} />);
    await screen.findByText('MIGROS');

    await user.type(screen.getByLabelText('Keyword'), 'COOP');
    await user.click(screen.getByRole('combobox', { name: 'Category' }));
    await user.click(await screen.findByRole('option', { name: 'Lebensmittel' }));
    await user.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() =>
      expect(categoryRulesApi.create).toHaveBeenCalledWith('test-token', {
        keyword: 'COOP',
        categoryId: 'c1',
      }),
    );
  });

  it('deletes a rule after confirmation', async () => {
    vi.mocked(categoryRulesApi.delete).mockResolvedValue(undefined);
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    renderWithProviders(<CategoryRulesDialog onClose={vi.fn()} />);
    await screen.findByText('MIGROS');

    await user.click(screen.getAllByRole('button', { name: 'Delete rule' })[0]);

    expect(globalThis.confirm).toHaveBeenCalledWith('Delete rule “MIGROS”?');
    await waitFor(() => expect(categoryRulesApi.delete).toHaveBeenCalledWith('test-token', 'r1'));
  });

  it('does not delete when the confirmation is declined', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderWithProviders(<CategoryRulesDialog onClose={vi.fn()} />);
    await screen.findByText('MIGROS');

    await user.click(screen.getAllByRole('button', { name: 'Delete rule' })[0]);

    expect(categoryRulesApi.delete).not.toHaveBeenCalled();
  });

  async function submitDuplicateKeyword(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText('Keyword'), 'MIGROS');
    await user.click(screen.getByRole('combobox', { name: 'Category' }));
    await user.click(await screen.findByRole('option', { name: 'Lebensmittel' }));
    await user.click(screen.getByRole('button', { name: 'Add' }));
  }

  it('shows the duplicate-keyword message on a 409', async () => {
    // exactly what apiRequest throws: a typed error, not a message containing "409"
    vi.mocked(categoryRulesApi.create).mockRejectedValue(
      new ApiError('A rule with this keyword already exists', 409),
    );
    const user = userEvent.setup();
    renderWithProviders(<CategoryRulesDialog onClose={vi.fn()} />);
    await screen.findByText('MIGROS');

    await submitDuplicateKeyword(user);

    expect(
      await screen.findByText('A rule for this keyword already exists'),
    ).toBeInTheDocument();
  });

  it('shows the backend detail for any other error', async () => {
    vi.mocked(categoryRulesApi.create).mockRejectedValue(new ApiError('Category not found', 404));
    const user = userEvent.setup();
    renderWithProviders(<CategoryRulesDialog onClose={vi.fn()} />);
    await screen.findByText('MIGROS');

    await submitDuplicateKeyword(user);

    expect(await screen.findByText('Category not found')).toBeInTheDocument();
  });

  it('edits a rule via the inline form', async () => {
    vi.mocked(categoryRulesApi.update).mockResolvedValue({
      ...rules[0],
      keyword: 'MIGROS AG',
    });
    const user = userEvent.setup();
    renderWithProviders(<CategoryRulesDialog onClose={vi.fn()} />);
    await screen.findByText('MIGROS');

    await user.click(screen.getAllByRole('button', { name: 'Edit rule' })[0]);
    const keywordInput = screen.getAllByLabelText('Keyword')[0];
    expect(keywordInput).toHaveValue('MIGROS');
    await user.type(keywordInput, ' AG');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(categoryRulesApi.update).toHaveBeenCalledWith('test-token', 'r1', {
        keyword: 'MIGROS AG',
        categoryId: 'c1',
      }),
    );
  });
});
