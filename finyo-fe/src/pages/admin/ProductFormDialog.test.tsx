import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProductFormDialog } from './ProductFormDialog';
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
    createProduct: vi.fn(),
    updateProduct: vi.fn(),
  },
}));

interface FormValues {
  provider?: string;
  name?: string;
  isin?: string;
  valor?: string;
  equityPct?: string;
  terPct?: string;
}

async function fillForm(user: ReturnType<typeof userEvent.setup>, values: FormValues) {
  const fields: Array<[string, string | undefined]> = [
    ['Provider', values.provider],
    ['Name', values.name],
    ['ISIN', values.isin],
    ['Valor', values.valor],
    ['Equity (%)', values.equityPct],
    ['TER (%)', values.terPct],
  ];
  for (const [label, value] of fields) {
    if (value !== undefined) await user.type(screen.getByLabelText(label), value);
  }
}

const validValues: FormValues = {
  provider: 'frankly',
  name: 'Extreme 95',
  isin: 'CH0473243999',
  equityPct: '95',
  terPct: '0.44',
};

describe('ProductFormDialog', () => {
  it('blocks the submit on an invalid ISIN', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProductFormDialog product={null} onClose={vi.fn()} />);

    await fillForm(user, { ...validValues, isin: 'INVALID' });
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(
      screen.getByText('ISIN must be 12 characters: 2 letters, 9 alphanumeric characters, 1 digit'),
    ).toBeInTheDocument();
    expect(pillar3Api.createProduct).not.toHaveBeenCalled();
  });

  it('blocks the submit when the equity share exceeds 100', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProductFormDialog product={null} onClose={vi.fn()} />);

    await fillForm(user, { ...validValues, equityPct: '150' });
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(screen.getByText('Equity share must be between 0 and 100')).toBeInTheDocument();
    expect(pillar3Api.createProduct).not.toHaveBeenCalled();
  });

  it('blocks the submit when the TER exceeds 10', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProductFormDialog product={null} onClose={vi.fn()} />);

    await fillForm(user, { ...validValues, terPct: '12' });
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(screen.getByText('TER must be between 0 and 10')).toBeInTheDocument();
    expect(pillar3Api.createProduct).not.toHaveBeenCalled();
  });

  it('blocks the submit on a non-numeric valor', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProductFormDialog product={null} onClose={vi.fn()} />);

    await fillForm(user, { ...validValues, valor: 'not-a-number' });
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(screen.getByText('Valor must contain digits only')).toBeInTheDocument();
    expect(pillar3Api.createProduct).not.toHaveBeenCalled();
  });

  it('requires provider and name', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProductFormDialog product={null} onClose={vi.fn()} />);

    await fillForm(user, { ...validValues, provider: undefined, name: undefined });
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(screen.getByText('Provider is required')).toBeInTheDocument();
    expect(screen.getByText('Name is required')).toBeInTheDocument();
    expect(pillar3Api.createProduct).not.toHaveBeenCalled();
  });

  it('submits valid input with a normalised ISIN and closes on success', async () => {
    vi.mocked(pillar3Api.createProduct).mockResolvedValue(pillar3Product({ id: 'a' }));
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<ProductFormDialog product={null} onClose={onClose} />);

    await fillForm(user, { ...validValues, isin: 'ch0473243999' });
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(pillar3Api.createProduct).toHaveBeenCalledWith('test-token', {
      provider: 'frankly',
      name: 'Extreme 95',
      isin: 'CH0473243999',
      valor: null,
      equityPct: 95,
      terPct: 0.44,
      active: true,
      sortOrder: 0,
    });
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  });

  it('updates the product in edit mode and keeps its active flag', async () => {
    const product = pillar3Product({
      id: 'b',
      isin: 'CH0011111116',
      active: false,
      sortOrder: 5,
    });
    vi.mocked(pillar3Api.updateProduct).mockResolvedValue(product);
    const user = userEvent.setup();
    renderWithProviders(<ProductFormDialog product={product} onClose={vi.fn()} />);

    expect(screen.getByText('Edit Product')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(pillar3Api.updateProduct).toHaveBeenCalledWith(
      'test-token',
      'b',
      expect.objectContaining({ isin: 'CH0011111116', active: false, sortOrder: 5 }),
    );
    expect(pillar3Api.createProduct).not.toHaveBeenCalled();
  });

  it('closes without saving on cancel', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<ProductFormDialog product={null} onClose={onClose} />);

    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(pillar3Api.createProduct).not.toHaveBeenCalled();
  });
});
