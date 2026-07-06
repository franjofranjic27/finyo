import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Investments } from './Investments';
import { instrumentsApi } from '@/api/instruments';
import { renderWithProviders } from '@/test/test-utils';
import type { Instrument, MarketData } from '@/types';

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

vi.mock('@/api/instruments', () => ({
  instrumentsApi: {
    getAll: vi.fn(),
    getMarketData: vi.fn(),
    getAllMarketData: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

const nestle: Instrument = {
  id: 'i1',
  valor: '3886335',
  isin: 'CH0038863350',
  ticker: 'NESN',
  name: 'Nestlé SA',
  instrumentType: 'STOCK',
  sortOrder: 1,
};

const unknownInstrument: Instrument = {
  id: 'i2',
  isin: 'CH0000000000',
  sortOrder: 2,
};

const nestleMarketData: MarketData = {
  valor: '3886335',
  isin: 'CH0038863350',
  name: 'Nestlé SA',
  exchange: 'SIX',
  currency: 'CHF',
  lastPrice: 88.5,
  change1dPct: -1.25,
  change1wPct: 2.4,
  dividendYield: 3.41,
  instrumentType: 'STOCK',
};

describe('Investments', () => {
  it('shows an empty state when no instruments are tracked', async () => {
    vi.mocked(instrumentsApi.getAll).mockResolvedValue([]);
    vi.mocked(instrumentsApi.getAllMarketData).mockResolvedValue([]);
    renderWithProviders(<Investments />);

    expect(await screen.findByText('No instruments tracked yet')).toBeInTheDocument();
  });

  it('renders an instrument card with its market data', async () => {
    vi.mocked(instrumentsApi.getAll).mockResolvedValue([nestle]);
    vi.mocked(instrumentsApi.getAllMarketData).mockResolvedValue([nestleMarketData]);
    renderWithProviders(<Investments />);

    expect(await screen.findByText('Nestlé SA')).toBeInTheDocument();
    expect(screen.getByText('CHF 88.50')).toBeInTheDocument();
    expect(screen.getByText('1.25%')).toBeInTheDocument();
    expect(screen.getByText('3.41%')).toBeInTheDocument();
    expect(screen.getByText('SIX')).toBeInTheDocument();
    expect(screen.getByText(/ISIN: CH0038863350/)).toBeInTheDocument();
  });

  it('shows a fallback card when market data is missing', async () => {
    vi.mocked(instrumentsApi.getAll).mockResolvedValue([unknownInstrument]);
    vi.mocked(instrumentsApi.getAllMarketData).mockResolvedValue([]);
    renderWithProviders(<Investments />);

    expect(await screen.findByText('Market data unavailable')).toBeInTheDocument();
    expect(screen.getByText(/ISIN: CH0000000000/)).toBeInTheDocument();
  });

  it('deletes an instrument after confirmation', async () => {
    vi.mocked(instrumentsApi.getAll).mockResolvedValue([nestle]);
    vi.mocked(instrumentsApi.getAllMarketData).mockResolvedValue([nestleMarketData]);
    vi.mocked(instrumentsApi.delete).mockResolvedValue(undefined);
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    renderWithProviders(<Investments />);
    await screen.findByText('Nestlé SA');

    // The delete icon button is the only button without a text label on the page.
    const deleteButton = screen
      .getAllByRole('button')
      .find((button) => button.textContent?.trim() === '');
    expect(deleteButton).toBeDefined();
    await user.click(deleteButton!);

    expect(instrumentsApi.delete).toHaveBeenCalledWith('test-token', 'i1');
  });

  it('opens the add dialog and creates an instrument', async () => {
    vi.mocked(instrumentsApi.getAll).mockResolvedValue([]);
    vi.mocked(instrumentsApi.getAllMarketData).mockResolvedValue([]);
    vi.mocked(instrumentsApi.create).mockResolvedValue(nestle);
    const user = userEvent.setup();
    renderWithProviders(<Investments />);
    await screen.findByText('No instruments tracked yet');

    await user.click(screen.getAllByRole('button', { name: /Add Instrument/ })[0]);
    await user.type(screen.getByPlaceholderText('e.g. CH0012221716'), 'CH0038863350');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(instrumentsApi.create).toHaveBeenCalledWith('test-token', {
      valor: undefined,
      isin: 'CH0038863350',
      ticker: undefined,
      name: undefined,
      instrumentType: 'STOCK',
    });
  });
});
