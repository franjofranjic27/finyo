import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Pillar3ProductsAdminPage } from './Pillar3ProductsAdminPage';
import { pillar3Api } from '@/api/pillar3';
import { pillar3Product } from '@/test/fixtures/pillar3';
import { renderWithProviders } from '@/test/test-utils';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    user: { profile: {} },
    accessToken: 'test-token',
    roles: ['user', 'admin'],
    hasRole: () => true,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock('@/api/pillar3', () => ({
  pillar3Api: {
    adminList: vi.fn(),
    createProduct: vi.fn(),
    updateProduct: vi.fn(),
    deleteProduct: vi.fn(),
  },
}));

const products = [
  pillar3Product({
    id: 'a',
    provider: 'frankly',
    name: 'Extreme 95',
    isin: 'CH0473243999',
    valor: '47324399',
    equityPct: 95,
    terPct: 0.44,
    sortOrder: 1,
  }),
  pillar3Product({
    id: 'b',
    provider: 'VIAC',
    name: 'Global 40',
    isin: 'CH0011111116',
    equityPct: 40,
    terPct: 0.4,
    active: false,
    sortOrder: 2,
  }),
];

function rowOf(name: string): HTMLElement {
  const row = screen.getByText(name).closest('tr');
  if (!row) throw new Error(`No table row found for "${name}"`);
  return row;
}

describe('Pillar3ProductsAdminPage', () => {
  it('renders the product table with active and inactive badges', async () => {
    vi.mocked(pillar3Api.adminList).mockResolvedValue(products);
    renderWithProviders(<Pillar3ProductsAdminPage />);

    expect(await screen.findByText('Extreme 95')).toBeInTheDocument();
    expect(screen.getByText('frankly')).toBeInTheDocument();
    expect(screen.getByText('CH0473243999')).toBeInTheDocument();
    expect(screen.getByText('47324399')).toBeInTheDocument();

    expect(within(rowOf('Extreme 95')).getByText('Active')).toBeInTheDocument();
    expect(within(rowOf('Global 40')).getByText('Inactive')).toBeInTheDocument();
  });

  it('shows an empty state when there are no products', async () => {
    vi.mocked(pillar3Api.adminList).mockResolvedValue([]);
    renderWithProviders(<Pillar3ProductsAdminPage />);

    expect(await screen.findByText(/No products yet/)).toBeInTheDocument();
  });

  it('creates a product with the entered values', async () => {
    vi.mocked(pillar3Api.adminList).mockResolvedValue([]);
    vi.mocked(pillar3Api.createProduct).mockResolvedValue(products[0]);
    const user = userEvent.setup();
    renderWithProviders(<Pillar3ProductsAdminPage />);

    await screen.findByText(/No products yet/);
    await user.click(screen.getAllByRole('button', { name: 'New Product' })[0]);

    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Provider'), 'frankly');
    await user.type(within(dialog).getByLabelText('Name'), 'Extreme 95');
    await user.type(within(dialog).getByLabelText('ISIN'), 'ch0473243999');
    await user.type(within(dialog).getByLabelText('Equity (%)'), '95');
    await user.type(within(dialog).getByLabelText('TER (%)'), '0.44');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(pillar3Api.createProduct).toHaveBeenCalledWith('test-token', {
        provider: 'frankly',
        name: 'Extreme 95',
        isin: 'CH0473243999',
        valor: null,
        equityPct: 95,
        terPct: 0.44,
        active: true,
        sortOrder: 0,
      }),
    );
    // Dialog closes after a successful save.
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('prefills the edit dialog and updates the product', async () => {
    vi.mocked(pillar3Api.adminList).mockResolvedValue(products);
    vi.mocked(pillar3Api.updateProduct).mockResolvedValue(products[0]);
    const user = userEvent.setup();
    renderWithProviders(<Pillar3ProductsAdminPage />);

    await screen.findByText('Extreme 95');
    await user.click(within(rowOf('Extreme 95')).getByRole('button', { name: 'Edit' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Edit Product')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Provider')).toHaveValue('frankly');
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Extreme 95');
    expect(within(dialog).getByLabelText('ISIN')).toHaveValue('CH0473243999');
    expect(within(dialog).getByLabelText('Valor')).toHaveValue('47324399');
    expect(within(dialog).getByLabelText('Equity (%)')).toHaveValue(95);
    expect(within(dialog).getByLabelText('TER (%)')).toHaveValue(0.44);

    await user.clear(within(dialog).getByLabelText('Name'));
    await user.type(within(dialog).getByLabelText('Name'), 'Extreme 100');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(pillar3Api.updateProduct).toHaveBeenCalledWith('test-token', 'a', {
        provider: 'frankly',
        name: 'Extreme 100',
        isin: 'CH0473243999',
        valor: '47324399',
        equityPct: 95,
        terPct: 0.44,
        active: true,
        sortOrder: 1,
      }),
    );
  });

  it('deletes a product after confirmation', async () => {
    vi.mocked(pillar3Api.adminList).mockResolvedValue(products);
    vi.mocked(pillar3Api.deleteProduct).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderWithProviders(<Pillar3ProductsAdminPage />);

    await screen.findByText('Extreme 95');
    await user.click(within(rowOf('Extreme 95')).getByRole('button', { name: 'Delete' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/Delete "Extreme 95" permanently\?/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    await waitFor(() =>
      expect(pillar3Api.deleteProduct).toHaveBeenCalledWith('test-token', 'a'),
    );
  });

  it('deactivates an active product via the row action', async () => {
    vi.mocked(pillar3Api.adminList).mockResolvedValue(products);
    vi.mocked(pillar3Api.updateProduct).mockResolvedValue({ ...products[0], active: false });
    const user = userEvent.setup();
    renderWithProviders(<Pillar3ProductsAdminPage />);

    await screen.findByText('Extreme 95');
    await user.click(within(rowOf('Extreme 95')).getByRole('button', { name: 'Deactivate' }));

    await waitFor(() =>
      expect(pillar3Api.updateProduct).toHaveBeenCalledWith('test-token', 'a', {
        provider: 'frankly',
        name: 'Extreme 95',
        isin: 'CH0473243999',
        valor: '47324399',
        equityPct: 95,
        terPct: 0.44,
        active: false,
        sortOrder: 1,
      }),
    );
  });

  it('activates an inactive product via the row action', async () => {
    vi.mocked(pillar3Api.adminList).mockResolvedValue(products);
    vi.mocked(pillar3Api.updateProduct).mockResolvedValue({ ...products[1], active: true });
    const user = userEvent.setup();
    renderWithProviders(<Pillar3ProductsAdminPage />);

    await screen.findByText('Global 40');
    await user.click(within(rowOf('Global 40')).getByRole('button', { name: 'Activate' }));

    await waitFor(() =>
      expect(pillar3Api.updateProduct).toHaveBeenCalledWith(
        'test-token',
        'b',
        expect.objectContaining({ active: true }),
      ),
    );
  });

  it('shows the server error on a duplicate ISIN (409)', async () => {
    vi.mocked(pillar3Api.adminList).mockResolvedValue([]);
    vi.mocked(pillar3Api.createProduct).mockRejectedValue(
      new Error('A product with ISIN CH0473243999 already exists'),
    );
    const user = userEvent.setup();
    renderWithProviders(<Pillar3ProductsAdminPage />);

    await screen.findByText(/No products yet/);
    await user.click(screen.getAllByRole('button', { name: 'New Product' })[0]);

    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Provider'), 'frankly');
    await user.type(within(dialog).getByLabelText('Name'), 'Extreme 95');
    await user.type(within(dialog).getByLabelText('ISIN'), 'CH0473243999');
    await user.type(within(dialog).getByLabelText('Equity (%)'), '95');
    await user.type(within(dialog).getByLabelText('TER (%)'), '0.44');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    expect(
      await within(dialog).findByText('A product with ISIN CH0473243999 already exists'),
    ).toBeInTheDocument();
    // Dialog stays open so the user can correct the input.
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
