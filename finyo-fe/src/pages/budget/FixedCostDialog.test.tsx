import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FixedCostDialog } from './FixedCostDialog';
import { budgetApi } from '@/api/budget';
import { renderWithProviders } from '@/test/test-utils';
import { fixedCost } from '@/test/fixtures/budget';

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

vi.mock('@/api/budget', () => ({
  budgetApi: {
    createFixedCost: vi.fn(),
    updateFixedCost: vi.fn(),
  },
}));

const CATEGORIES = ['Versicherung', 'Sport'];

function renderDialog() {
  return renderWithProviders(
    <FixedCostDialog fixedCost={null} categories={CATEGORIES} onClose={vi.fn()} />,
  );
}

describe('FixedCostDialog', () => {
  beforeEach(() => {
    vi.mocked(budgetApi.createFixedCost).mockResolvedValue(fixedCost({ id: 'fc-new' }));
  });

  describe('category combobox', () => {
    it('suggests the existing categories on focus and filters them while typing', async () => {
      const user = userEvent.setup();
      renderDialog();

      await user.click(screen.getByLabelText('Category'));
      expect(screen.getByRole('option', { name: 'Versicherung' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'Sport' })).toBeInTheDocument();

      await user.keyboard('vers');
      expect(screen.getByRole('option', { name: 'Versicherung' })).toBeInTheDocument();
      expect(screen.queryByRole('option', { name: 'Sport' })).not.toBeInTheDocument();
    });

    it('fills the input with a clicked suggestion', async () => {
      const user = userEvent.setup();
      renderDialog();

      await user.click(screen.getByLabelText('Category'));
      await user.click(screen.getByRole('option', { name: 'Versicherung' }));

      expect(screen.getByLabelText('Category')).toHaveValue('Versicherung');
      expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    });

    it('selects a suggestion with arrow keys and enter', async () => {
      const user = userEvent.setup();
      renderDialog();

      await user.click(screen.getByLabelText('Category'));
      await user.keyboard('{ArrowDown}{ArrowDown}{Enter}');

      expect(screen.getByLabelText('Category')).toHaveValue('Sport');
    });

    it('wraps to the last suggestion when arrowing up from the neutral state', async () => {
      const user = userEvent.setup();
      renderDialog();

      await user.click(screen.getByLabelText('Category'));
      await user.keyboard('{ArrowUp}{Enter}');

      expect(screen.getByLabelText('Category')).toHaveValue('Sport');
    });

    it('exposes the highlighted option via aria-activedescendant', async () => {
      const user = userEvent.setup();
      renderDialog();

      const input = screen.getByLabelText('Category');
      await user.click(input);
      expect(input).not.toHaveAttribute('aria-activedescendant');

      await user.keyboard('{ArrowDown}');

      const option = screen.getByRole('option', { name: 'Versicherung' });
      expect(option.id).not.toBe('');
      expect(input).toHaveAttribute('aria-activedescendant', option.id);
      expect(option).toHaveAttribute('aria-selected', 'true');
    });

    it('closes only the suggestion panel on the first escape, the dialog on the second', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      renderWithProviders(
        <FixedCostDialog fixedCost={null} categories={CATEGORIES} onClose={onClose} />,
      );

      await user.click(screen.getByLabelText('Category'));
      expect(screen.getByRole('listbox')).toBeInTheDocument();

      await user.keyboard('{Escape}');
      expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
      expect(onClose).not.toHaveBeenCalled();

      await user.keyboard('{Escape}');
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('lets escape close the dialog directly when there are no suggestions', async () => {
      const user = userEvent.setup();
      const onClose = vi.fn();
      renderWithProviders(<FixedCostDialog fixedCost={null} categories={[]} onClose={onClose} />);

      await user.click(screen.getByLabelText('Category'));
      await user.keyboard('{Escape}');

      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('offers creating a value that matches no category and submits it', async () => {
      const user = userEvent.setup();
      renderDialog();

      await user.type(screen.getByLabelText('Category'), 'Wohnen');
      await user.click(screen.getByRole('option', { name: 'Create “Wohnen”' }));
      expect(screen.getByLabelText('Category')).toHaveValue('Wohnen');

      await user.type(screen.getByLabelText('Name'), 'Miete');
      await user.type(screen.getByLabelText('Amount (CHF)'), '1500');
      await user.click(screen.getByRole('button', { name: 'Add' }));

      await waitFor(() =>
        expect(budgetApi.createFixedCost).toHaveBeenCalledWith('test-token', {
          name: 'Miete',
          category: 'Wohnen',
          paymentInterval: 'MONTHLY',
          amount: 1500,
        }),
      );
    });
  });

  describe('amount field', () => {
    it('labels the amount as a CHF field with a visible affix', () => {
      renderDialog();

      expect(screen.getByLabelText('Amount (CHF)')).toBeInTheDocument();
      // the aria-hidden affix inside the input wrapper
      expect(screen.getByText('CHF')).toBeInTheDocument();
    });

    it('truncates typed amounts to two decimals', async () => {
      const user = userEvent.setup();
      renderDialog();

      const amountInput = screen.getByLabelText('Amount (CHF)');
      await user.type(amountInput, '18.905');

      expect(amountInput).toHaveValue(18.9);
    });
  });
});
