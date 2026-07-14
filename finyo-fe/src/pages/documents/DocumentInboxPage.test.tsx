import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DocumentInboxPage } from './DocumentInboxPage';
import { documentsApi } from '@/api/documents';
import type { DocumentResponse } from '@/api/documents';
import { renderWithProviders } from '@/test/test-utils';

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

vi.mock('@/api/documents', () => ({
  documentsApi: {
    getAll: vi.fn(),
    sync: vi.fn(),
    apply: vi.fn(),
    dismiss: vi.fn(),
  },
}));

function taxDocument(overrides: Partial<DocumentResponse> = {}): DocumentResponse {
  return {
    id: 'doc-1',
    filename: 'lohnausweis.pdf',
    sourcePath: '/Steuern/STE-2025/Lohnausweis',
    status: 'AUTO_APPLIED',
    detectedType: 'SALARY_CERTIFICATE',
    folderType: 'SALARY_CERTIFICATE',
    confidence: 0.93,
    detectedTaxYear: 2025,
    folderTaxYear: 2025,
    extractedFields: [],
    failureReason: null,
    deletedInDrive: false,
    createdAt: '2026-07-14T08:00:00Z',
    ...overrides,
  };
}

const securitiesInReview = taxDocument({
  id: 'doc-3',
  filename: 'wertschriften.pdf',
  sourcePath: '/Steuern/STE-2024/Wertschriften',
  status: 'NEEDS_REVIEW',
  detectedType: 'SECURITIES_STATEMENT',
  folderType: 'SECURITIES_STATEMENT',
  confidence: 0.71,
  detectedTaxYear: 2025,
  folderTaxYear: 2024,
  extractedFields: [
    {
      fieldName: 'totalTaxValue',
      label: 'Steuerwert total',
      value: 80_000,
      targetTaxYearField: 'netWealth',
    },
    {
      fieldName: 'totalGrossIncome',
      label: 'Bruttoertrag total',
      value: 1200,
      targetTaxYearField: 'investmentIncome',
    },
    {
      fieldName: 'totalFees',
      label: 'Gebühren total',
      value: 150,
      targetTaxYearField: null,
    },
  ],
});

describe('DocumentInboxPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('lists the synced documents with their status', async () => {
    vi.mocked(documentsApi.getAll).mockResolvedValue([
      taxDocument(),
      taxDocument({
        id: 'doc-2',
        filename: 'scan.pdf',
        sourcePath: '/Steuern/STE-2025/Diverses',
        status: 'FAILED',
        detectedType: null,
        folderType: null,
        confidence: null,
        detectedTaxYear: null,
        failureReason: 'Das PDF enthält keinen auswertbaren Text.',
      }),
    ]);

    renderWithProviders(<DocumentInboxPage />);

    expect(await screen.findByText('lohnausweis.pdf')).toBeInTheDocument();
    expect(screen.getByText('Applied automatically')).toBeInTheDocument();
    expect(screen.getByText('/Steuern/STE-2025/Lohnausweis')).toBeInTheDocument();
    expect(screen.getByText('Document: Salary certificate')).toBeInTheDocument();
    expect(screen.getByText('Document year 2025')).toBeInTheDocument();

    expect(screen.getByText('scan.pdf')).toBeInTheDocument();
    expect(screen.getByText('Failed')).toBeInTheDocument();
    expect(screen.getByText('Das PDF enthält keinen auswertbaren Text.')).toBeInTheDocument();
  });

  it('explains a year mismatch as the reason for the review', async () => {
    vi.mocked(documentsApi.getAll).mockResolvedValue([securitiesInReview]);

    renderWithProviders(<DocumentInboxPage />);

    expect(await screen.findByText('Needs review')).toBeInTheDocument();
    expect(
      screen.getByText('The folder says tax year 2024, but the document names 2025.'),
    ).toBeInTheDocument();
  });

  it('applies the checked fields to the corrected tax year', async () => {
    vi.mocked(documentsApi.getAll).mockResolvedValue([securitiesInReview]);
    vi.mocked(documentsApi.apply).mockResolvedValue(
      taxDocument({ id: 'doc-3', status: 'APPLIED' }),
    );
    const user = userEvent.setup();

    renderWithProviders(<DocumentInboxPage />);

    // Info-only values have no checkbox and cannot be applied.
    expect(await screen.findByRole('checkbox', { name: 'Steuerwert total' })).toBeChecked();
    expect(screen.getAllByRole('checkbox')).toHaveLength(2);
    expect(screen.getByText('info only')).toBeInTheDocument();

    await user.click(screen.getByRole('checkbox', { name: 'Bruttoertrag total' }));

    const yearInput = screen.getByLabelText('Target tax year');
    await user.clear(yearInput);
    await user.type(yearInput, '2024');

    await user.click(screen.getByRole('button', { name: 'Apply' }));

    await waitFor(() =>
      expect(documentsApi.apply).toHaveBeenCalledWith('test-token', 'doc-3', {
        year: 2024,
        fieldNames: ['totalTaxValue'],
      }),
    );
  });

  it('shows the sync result after a manual synchronisation', async () => {
    vi.mocked(documentsApi.getAll).mockResolvedValue([]);
    vi.mocked(documentsApi.sync).mockResolvedValue({
      autoApplied: 2,
      needsReview: 1,
      failed: 0,
    });
    const user = userEvent.setup();

    renderWithProviders(<DocumentInboxPage />);

    await user.click(screen.getByRole('button', { name: 'Synchronise now' }));

    expect(await screen.findByText('2 applied · 1 to review · 0 failed')).toBeInTheDocument();
    expect(documentsApi.sync).toHaveBeenCalledWith('test-token');
  });

  it('renders an empty state when no documents were synced yet', async () => {
    vi.mocked(documentsApi.getAll).mockResolvedValue([]);

    renderWithProviders(<DocumentInboxPage />);

    expect(await screen.findByText('No documents yet')).toBeInTheDocument();
    expect(
      screen.getByText('Synchronise to import the tax documents from your SharePoint folder.'),
    ).toBeInTheDocument();
  });
});
