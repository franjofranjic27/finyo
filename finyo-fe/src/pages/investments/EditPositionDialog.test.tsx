import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EditPositionDialog } from './EditPositionDialog';
import type { EditPositionInitialValues } from './EditPositionDialog';
import { positionDetailApi } from '@/api/positionDetail';
import { renderWithProviders } from '@/test/test-utils';
import { positionDetail } from '@/test/fixtures/portfolio';

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

vi.mock('@/api/positionDetail', () => ({
  positionDetailApi: { updatePosition: vi.fn() },
}));

const initial: EditPositionInitialValues = {
  quantity: 10,
  purchasePrice: 90,
  purchaseDate: '2026-01-15',
  currentPrice: 100,
};

function renderDialog(overrides: Partial<EditPositionInitialValues> = {}) {
  const onClose = vi.fn();
  const result = renderWithProviders(
    <EditPositionDialog positionId="p1" initial={{ ...initial, ...overrides }} onClose={onClose} />,
  );
  return { onClose, ...result };
}

describe('EditPositionDialog', () => {
  beforeEach(() => {
    vi.mocked(positionDetailApi.updatePosition).mockReset();
    vi.mocked(positionDetailApi.updatePosition).mockResolvedValue(
      positionDetail({ positionId: 'p1' }),
    );
  });

  it('renders the initial holding values', () => {
    renderDialog();

    expect(screen.getByRole('heading', { name: 'Edit position' })).toBeInTheDocument();
    expect(screen.getByLabelText('Quantity')).toHaveValue(10);
    expect(screen.getByLabelText('Avg. Purchase Price')).toHaveValue(90);
    expect(screen.getByLabelText('Purchase Date')).toHaveValue('2026-01-15');
    expect(screen.getByLabelText('Current Price (optional)')).toHaveValue(100);
    expect(
      screen.getByText('Overrides the market price — a live price refresh may replace it again'),
    ).toBeInTheDocument();
  });

  it('PATCHes the full field set, invalidates the queries and closes', async () => {
    const user = userEvent.setup();
    const { onClose, queryClient } = renderDialog();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const quantityInput = screen.getByLabelText('Quantity');
    await user.clear(quantityInput);
    await user.type(quantityInput, '12');
    await user.clear(screen.getByLabelText('Purchase Date'));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    // untouched currentPrice is never sent — it may show a live market price
    await waitFor(() =>
      expect(positionDetailApi.updatePosition).toHaveBeenCalledWith('test-token', 'p1', {
        quantity: 12,
        purchasePrice: 90,
        purchaseDate: null,
      }),
    );
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['position', 'p1'] }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['portfolio'] });
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it('sends the current price only when the user changed it', async () => {
    const user = userEvent.setup();
    renderDialog();

    const currentPriceInput = screen.getByLabelText('Current Price (optional)');
    await user.clear(currentPriceInput);
    await user.type(currentPriceInput, '120');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(positionDetailApi.updatePosition).toHaveBeenCalledWith('test-token', 'p1', {
        quantity: 10,
        purchasePrice: 90,
        purchaseDate: '2026-01-15',
        currentPrice: 120,
      }),
    );
  });

  it('rejects a non-positive quantity client-side without calling the API', async () => {
    const user = userEvent.setup();
    renderDialog();

    const quantityInput = screen.getByLabelText('Quantity');
    await user.clear(quantityInput);
    await user.type(quantityInput, '0');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Quantity must be greater than 0')).toBeInTheDocument();
    expect(positionDetailApi.updatePosition).not.toHaveBeenCalled();
  });

  it('rejects a negative purchase price client-side', async () => {
    const user = userEvent.setup();
    renderDialog();

    const priceInput = screen.getByLabelText('Avg. Purchase Price');
    await user.clear(priceInput);
    await user.type(priceInput, '-1');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Purchase price must be 0 or greater')).toBeInTheDocument();
    expect(positionDetailApi.updatePosition).not.toHaveBeenCalled();
  });
});
