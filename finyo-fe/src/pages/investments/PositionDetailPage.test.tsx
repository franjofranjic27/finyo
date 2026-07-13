import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { PositionDetailPage } from './PositionDetailPage';
import { Breadcrumb } from '@/components/layout/Breadcrumb';
import {
  BreadcrumbProvider,
  useBreadcrumbSegments,
} from '@/components/layout/BreadcrumbContext';
import { portfolioApi } from '@/api/portfolio';
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

vi.mock('@/api/portfolio', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/portfolio')>();
  return {
    ...actual,
    portfolioApi: { deletePosition: vi.fn() },
  };
});

vi.mock('@/api/positionDetail', () => ({
  positionDetailApi: {
    getPosition: vi.fn(),
    updatePosition: vi.fn(),
    updateInstrument: vi.fn(),
    uploadFactsheet: vi.fn(),
    fetchFactsheet: vi.fn(),
    deleteFactsheet: vi.fn(),
  },
}));

/** Renders the header breadcrumb exactly as the app layout would. */
function BreadcrumbProbe() {
  const segments = useBreadcrumbSegments();
  return segments ? <Breadcrumb segments={segments} /> : null;
}

function renderPage() {
  return renderWithProviders(
    <BreadcrumbProvider>
      <BreadcrumbProbe />
      <Routes>
        <Route path="/investments" element={<div>portfolio-page</div>} />
        <Route path="/investments/positions/:positionId" element={<PositionDetailPage />} />
      </Routes>
    </BreadcrumbProvider>,
    { route: '/investments/positions/p1' },
  );
}

const detail = positionDetail({
  positionId: 'p1',
  name: 'Nestlé SA',
  isin: 'CH0038863350',
  valor: '3886335',
  assetClass: 'STOCK',
  ter: 0.15,
  currency: 'CHF',
  quantity: 10,
  avgPurchasePrice: 90,
  purchaseDate: '2026-01-15',
  currentPrice: 100,
  value: 1000,
  gainLoss: 100,
  returnPercent: 11.1,
  portfolioShare: 60,
});

describe('PositionDetailPage', () => {
  beforeEach(() => {
    vi.mocked(positionDetailApi.getPosition).mockResolvedValue(detail);
    vi.mocked(positionDetailApi.updatePosition).mockResolvedValue(detail);
    vi.mocked(positionDetailApi.updateInstrument).mockResolvedValue(detail);
    vi.mocked(positionDetailApi.uploadFactsheet).mockResolvedValue(detail);
    vi.mocked(positionDetailApi.deleteFactsheet).mockResolvedValue(undefined);
    vi.mocked(portfolioApi.deletePosition).mockResolvedValue(undefined);
  });

  it('renders the instrument header, stat tiles and product information', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Nestlé SA', level: 2 })).toBeInTheDocument();
    expect(screen.getByText('Price live · SIX')).toBeInTheDocument();
    expect(screen.getByText('CH0038863350 · CHF')).toBeInTheDocument();

    // Stat tiles
    expect(screen.getByText('Value')).toBeInTheDocument();
    expect(screen.getAllByText("CHF 1'000.00").length).toBeGreaterThan(0);
    expect(screen.getByText('CHF 90.00')).toBeInTheDocument();
    expect(screen.getByText('CHF 100.00 · 11.1%')).toBeInTheDocument();

    // Product information rows ("Individual stocks" also renders as header chip)
    expect(screen.getByText('Product Information')).toBeInTheDocument();
    expect(screen.getAllByText('Individual stocks')).toHaveLength(2);
    expect(screen.getByText('3886335')).toBeInTheDocument();
    expect(screen.getByText('0.15 %')).toBeInTheDocument();
    expect(screen.getByText('Live (SIX)')).toBeInTheDocument();
    expect(screen.getByText('60.0%')).toBeInTheDocument();

    expect(positionDetailApi.getPosition).toHaveBeenCalledWith('test-token', 'p1');
  });

  it('hides the live chip when the price is not live', async () => {
    vi.mocked(positionDetailApi.getPosition).mockResolvedValue({
      ...detail,
      priceSource: 'CACHE',
    });
    renderPage();

    await screen.findByRole('heading', { name: 'Nestlé SA', level: 2 });
    expect(screen.queryByText('Price live · SIX')).not.toBeInTheDocument();
  });

  it('publishes the breadcrumb "Portfolio / Position – {name}"', async () => {
    renderPage();

    await screen.findByRole('heading', { name: 'Nestlé SA', level: 2 });
    const parent = screen.getByRole('link', { name: 'Portfolio' });
    expect(parent).toHaveAttribute('href', '/investments');
    expect(
      screen.getByRole('heading', { name: 'Position – Nestlé SA', level: 1 }),
    ).toBeInTheDocument();
  });

  it('PATCHes the instrument from the edit dialog and refreshes the queries', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderPage();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    const nameInput = screen.getByLabelText('Title');
    await user.clear(nameInput);
    await user.type(nameInput, 'Nestlé SA (Reg)');
    const terInput = screen.getByLabelText('TER (%)');
    await user.clear(terInput);
    await user.type(terInput, '0.2');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(positionDetailApi.updateInstrument).toHaveBeenCalledWith('test-token', 'p1', {
        name: 'Nestlé SA (Reg)',
        assetClass: 'STOCK',
        valor: '3886335',
        ter: 0.2,
        factsheetUrl: null,
      }),
    );
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['position', 'p1'] }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['portfolio'] });
  });

  it('PATCHes the holding from the edit-holding dialog and refreshes the queries', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderPage();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.click(await screen.findByRole('button', { name: 'Edit position' }));
    // the dialog is prefilled from the loaded detail
    expect(screen.getByLabelText('Quantity')).toHaveValue(10);
    expect(screen.getByLabelText('Purchase Date')).toHaveValue('2026-01-15');

    const quantityInput = screen.getByLabelText('Quantity');
    await user.clear(quantityInput);
    await user.type(quantityInput, '12');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(positionDetailApi.updatePosition).toHaveBeenCalledWith('test-token', 'p1', {
        quantity: 12,
        purchasePrice: 90,
        purchaseDate: '2026-01-15',
      }),
    );
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['position', 'p1'] }),
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['portfolio'] });
  });

  it('rejects a TER above 10 client-side without calling the API', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Edit' }));
    const terInput = screen.getByLabelText('TER (%)');
    await user.clear(terInput);
    await user.type(terInput, '10.01');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('TER must be a number between 0 and 10')).toBeInTheDocument();
    expect(positionDetailApi.updateInstrument).not.toHaveBeenCalled();
  });

  it('uploads a factsheet PDF through the dropzone input', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Upload factsheet (PDF) · max. 10 MB');
    const file = new File(['%PDF-1.7'], 'factsheet.pdf', { type: 'application/pdf' });
    await user.upload(screen.getByLabelText('Upload factsheet (PDF) · max. 10 MB'), file);

    await waitFor(() =>
      expect(positionDetailApi.uploadFactsheet).toHaveBeenCalledWith('test-token', 'p1', file),
    );
  });

  it('rejects non-PDF files client-side without calling the API', async () => {
    const user = userEvent.setup({ applyAccept: false });
    renderPage();

    await screen.findByText('Upload factsheet (PDF) · max. 10 MB');
    const file = new File(['not a pdf'], 'notes.txt', { type: 'text/plain' });
    await user.upload(screen.getByLabelText('Upload factsheet (PDF) · max. 10 MB'), file);

    expect(await screen.findByText('Only PDF files are allowed')).toBeInTheDocument();
    expect(positionDetailApi.uploadFactsheet).not.toHaveBeenCalled();
  });

  it('rejects PDFs above 10 MB client-side', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Upload factsheet (PDF) · max. 10 MB');
    const file = new File(['%PDF-1.7'], 'huge.pdf', { type: 'application/pdf' });
    Object.defineProperty(file, 'size', { value: 11 * 1024 * 1024 });
    await user.upload(screen.getByLabelText('Upload factsheet (PDF) · max. 10 MB'), file);

    expect(await screen.findByText('The file exceeds 10 MB')).toBeInTheDocument();
    expect(positionDetailApi.uploadFactsheet).not.toHaveBeenCalled();
  });

  it('shows the uploaded factsheet with size and date instead of the dropzone', async () => {
    vi.mocked(positionDetailApi.getPosition).mockResolvedValue({
      ...detail,
      factsheet: {
        filename: 'nestle-factsheet.pdf',
        sizeBytes: 2 * 1024 * 1024,
        uploadedAt: '2026-07-01T10:00:00Z',
      },
    });
    renderPage();

    expect(await screen.findByText('nestle-factsheet.pdf')).toBeInTheDocument();
    expect(screen.getByText(/2\.0 MB · 01 Jul 2026/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'View' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Replace' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete factsheet' })).toBeInTheDocument();
    expect(screen.queryByText('Upload factsheet (PDF) · max. 10 MB')).not.toBeInTheDocument();
  });

  it('shows the external link row when only a factsheet URL is set', async () => {
    vi.mocked(positionDetailApi.getPosition).mockResolvedValue({
      ...detail,
      factsheetUrl: 'https://example.com/factsheet.pdf',
    });
    renderPage();

    expect(await screen.findByText('https://example.com/factsheet.pdf')).toBeInTheDocument();
    const link = screen.getByRole('link', { name: 'Open factsheet link' });
    expect(link).toHaveAttribute('href', 'https://example.com/factsheet.pdf');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('shows the uploaded file and the external link together — they are independent', async () => {
    vi.mocked(positionDetailApi.getPosition).mockResolvedValue({
      ...detail,
      factsheetUrl: 'https://example.com/factsheet.pdf',
      factsheet: {
        filename: 'nestle-factsheet.pdf',
        sizeBytes: 2 * 1024 * 1024,
        uploadedAt: '2026-07-01T10:00:00Z',
      },
    });
    renderPage();

    expect(await screen.findByText('nestle-factsheet.pdf')).toBeInTheDocument();
    const link = screen.getByRole('link', { name: 'Open factsheet link' });
    expect(link).toHaveAttribute('href', 'https://example.com/factsheet.pdf');
    expect(screen.getByRole('button', { name: 'View' })).toBeInTheDocument();
  });

  it('deletes the position after confirmation and navigates back to the portfolio', async () => {
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Delete position' }));

    expect(globalThis.confirm).toHaveBeenCalledWith('Delete position "Nestlé SA"?');
    await waitFor(() =>
      expect(portfolioApi.deletePosition).toHaveBeenCalledWith('test-token', 'p1'),
    );
    expect(await screen.findByText('portfolio-page')).toBeInTheDocument();
  });

  it('shows the load error when the position cannot be fetched', async () => {
    vi.mocked(positionDetailApi.getPosition).mockRejectedValue(new Error('boom'));
    renderPage();

    expect(await screen.findByText('Failed to load the position')).toBeInTheDocument();
  });
});
