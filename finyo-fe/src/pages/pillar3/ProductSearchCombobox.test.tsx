import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProductSearchCombobox } from './ProductSearchCombobox';
import { pillar3Product } from '@/test/fixtures/pillar3';
import { renderWithProviders } from '@/test/test-utils';

const products = [
  pillar3Product({ id: 'a', name: 'Alpha Fund', provider: 'frankly', isin: 'CH0011111116', valor: '111' }),
  pillar3Product({ id: 'b', name: 'Beta Fund', provider: 'VIAC', isin: 'CH0022222229' }),
  pillar3Product({ id: 'c', name: 'Gamma Fund', provider: 'finpension', isin: 'LU0033333338' }),
];

function input() {
  return screen.getByRole('textbox', { name: 'Add product' });
}

describe('ProductSearchCombobox', () => {
  it('lists all unselected products on focus', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ProductSearchCombobox products={products} selectedIds={[]} onSelect={vi.fn()} />,
    );

    await user.click(input());

    expect(screen.getByText('Alpha Fund')).toBeInTheDocument();
    expect(screen.getByText('Beta Fund')).toBeInTheDocument();
    expect(screen.getByText('Gamma Fund')).toBeInTheDocument();
  });

  it('filters by name', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ProductSearchCombobox products={products} selectedIds={[]} onSelect={vi.fn()} />,
    );

    await user.type(input(), 'beta');

    expect(screen.getByText('Beta Fund')).toBeInTheDocument();
    expect(screen.queryByText('Alpha Fund')).not.toBeInTheDocument();
  });

  it('filters by ISIN', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ProductSearchCombobox products={products} selectedIds={[]} onSelect={vi.fn()} />,
    );

    await user.type(input(), 'lu0033');

    expect(screen.getByText('Gamma Fund')).toBeInTheDocument();
    expect(screen.queryByText('Alpha Fund')).not.toBeInTheDocument();
    expect(screen.queryByText('Beta Fund')).not.toBeInTheDocument();
  });

  it('excludes already-selected products from the results', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ProductSearchCombobox products={products} selectedIds={['a']} onSelect={vi.fn()} />,
    );

    await user.click(input());

    expect(screen.queryByText('Alpha Fund')).not.toBeInTheDocument();
    expect(screen.getByText('Beta Fund')).toBeInTheDocument();
  });

  it('shows a no-results message for an unmatched query', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ProductSearchCombobox products={products} selectedIds={[]} onSelect={vi.fn()} />,
    );

    await user.type(input(), 'does-not-exist');

    expect(screen.getByText('No matching products')).toBeInTheDocument();
  });

  it('selects a product on click and passes the full product to the callback', async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ProductSearchCombobox products={products} selectedIds={[]} onSelect={onSelect} />,
    );

    await user.type(input(), 'beta');
    await user.click(screen.getByRole('button', { name: /Beta Fund/ }));

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect).toHaveBeenCalledWith(products[1]);
    // The query is cleared and the list closed after picking.
    expect(input()).toHaveValue('');
    expect(screen.queryByText('Beta Fund')).not.toBeInTheDocument();
  });

  it('picks the first result on Enter', async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ProductSearchCombobox products={products} selectedIds={[]} onSelect={onSelect} />,
    );

    await user.type(input(), 'fund{Enter}');

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect).toHaveBeenCalledWith(products[0]);
  });

  it('closes the result list on Escape', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ProductSearchCombobox products={products} selectedIds={[]} onSelect={vi.fn()} />,
    );

    await user.type(input(), 'beta');
    expect(screen.getByText('Beta Fund')).toBeInTheDocument();

    await user.keyboard('{Escape}');

    expect(screen.queryByText('Beta Fund')).not.toBeInTheDocument();
  });

  it('renders a disabled input that opens no list', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ProductSearchCombobox products={products} selectedIds={[]} onSelect={vi.fn()} disabled />,
    );

    expect(input()).toBeDisabled();
    await user.click(input());
    expect(screen.queryByText('Alpha Fund')).not.toBeInTheDocument();
  });
});
