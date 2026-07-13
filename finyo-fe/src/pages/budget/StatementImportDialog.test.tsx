import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StatementImportDialog } from './StatementImportDialog';
import { accountsApi } from '@/api/accounts';
import { ApiError } from '@/api/client';
import { previewImport, transactionsApi } from '@/api/transactions';
import { renderWithProviders } from '@/test/test-utils';
import { bankAccount } from '@/test/fixtures/accounts';
import { importCommitResult, importPreview, importPreviewRow } from '@/test/fixtures/transactions';

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

vi.mock('@/api/accounts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/accounts')>();
  return {
    ...actual,
    accountsApi: { getAll: vi.fn() },
  };
});

vi.mock('@/api/transactions', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/transactions')>();
  return {
    ...actual,
    previewImport: vi.fn(),
    transactionsApi: {
      ...actual.transactionsApi,
      commitImport: vi.fn(),
    },
  };
});

const csvFile = new File(['03.07.2026;-42.50;MIGROS'], 'statement.csv', { type: 'text/csv' });

async function selectAccountAndUpload(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('combobox', { name: 'Account' }));
  await user.click(await screen.findByRole('option', { name: 'Privatkonto' }));
  await user.upload(screen.getByLabelText('Choose file'), csvFile);
}

describe('StatementImportDialog', () => {
  beforeEach(() => {
    vi.mocked(previewImport).mockReset();
    vi.mocked(transactionsApi.commitImport).mockReset();
    vi.mocked(accountsApi.getAll).mockResolvedValue([bankAccount({ id: 'a1' })]);
  });

  it('previews the file for the selected account and pre-checks new rows', async () => {
    vi.mocked(previewImport).mockResolvedValue(importPreview());
    const user = userEvent.setup();
    renderWithProviders(<StatementImportDialog open onOpenChange={vi.fn()} />);

    await selectAccountAndUpload(user);

    expect(await screen.findByText('MIGROS ZUERICH')).toBeInTheDocument();
    expect(previewImport).toHaveBeenCalledWith('test-token', csvFile, 'a1', {
      format: 'CSV',
      preset: 'CUSTOM',
    });

    expect(screen.getByText('2 new, 1 duplicates, 0 failed')).toBeInTheDocument();
    expect(screen.getByText('Duplicate')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Import row: MIGROS ZUERICH' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Import row: COOP PRONTO' })).not.toBeChecked();
    expect(screen.getByRole('button', { name: 'Import 2 transactions' })).toBeInTheDocument();
  });

  it('commits only the checked rows and shows the result summary', async () => {
    vi.mocked(previewImport).mockResolvedValue(importPreview());
    vi.mocked(transactionsApi.commitImport).mockResolvedValue(
      importCommitResult({ totalRows: 1, imported: 1 }),
    );
    const user = userEvent.setup();
    const { queryClient } = renderWithProviders(
      <StatementImportDialog open onOpenChange={vi.fn()} />,
    );
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await selectAccountAndUpload(user);
    await screen.findByText('MIGROS ZUERICH');

    await user.click(screen.getByRole('checkbox', { name: 'Import row: SBB EASYRIDE' }));
    await user.click(screen.getByRole('button', { name: 'Import 1 transactions' }));

    await waitFor(() =>
      expect(transactionsApi.commitImport).toHaveBeenCalledWith('test-token', {
        accountId: 'a1',
        format: 'CSV',
        skipDuplicates: true,
        rows: [
          {
            date: '2026-07-03',
            amount: -42.5,
            currency: 'CHF',
            description: 'MIGROS ZUERICH',
            externalRef: null,
            categoryId: 'c1',
          },
        ],
      }),
    );

    expect(await screen.findByText('1 imported, 0 skipped, 0 failed')).toBeInTheDocument();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['transactions'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['analytics'] });
  });

  it('renders the skipped count from the backend result fields', async () => {
    vi.mocked(previewImport).mockResolvedValue(importPreview());
    vi.mocked(transactionsApi.commitImport).mockResolvedValue(
      importCommitResult({ totalRows: 3, imported: 1, skippedDuplicates: 2 }),
    );
    const user = userEvent.setup();
    renderWithProviders(<StatementImportDialog open onOpenChange={vi.fn()} />);

    await selectAccountAndUpload(user);
    await screen.findByText('MIGROS ZUERICH');
    await user.click(screen.getByRole('button', { name: 'Import 2 transactions' }));

    expect(await screen.findByText('1 imported, 2 skipped, 0 failed')).toBeInTheDocument();
  });

  it('locks duplicates that carry a bank reference and lets heuristic duplicates be imported anyway', async () => {
    vi.mocked(previewImport).mockResolvedValue(
      importPreview({
        rows: [
          importPreviewRow({
            index: 0,
            description: 'COOP PRONTO',
            duplicate: true,
          }),
          importPreviewRow({
            index: 1,
            description: 'SBB EASYRIDE',
            duplicate: true,
            externalRef: 'ref-1',
          }),
        ],
      }),
    );
    vi.mocked(transactionsApi.commitImport).mockResolvedValue(importCommitResult());
    const user = userEvent.setup();
    renderWithProviders(<StatementImportDialog open onOpenChange={vi.fn()} />);

    await selectAccountAndUpload(user);
    await screen.findByText('COOP PRONTO');

    // a duplicate with a bank reference can never be re-imported — the unique index rejects it
    expect(screen.getByRole('checkbox', { name: 'Import row: SBB EASYRIDE' })).toBeDisabled();

    // the heuristic duplicate can be opted into, and the server-side re-check is then relaxed
    await user.click(screen.getByRole('checkbox', { name: 'Import row: COOP PRONTO' }));
    await user.click(screen.getByRole('button', { name: 'Import 1 transactions' }));

    await waitFor(() =>
      expect(transactionsApi.commitImport).toHaveBeenCalledWith(
        'test-token',
        expect.objectContaining({
          skipDuplicates: false,
          rows: [expect.objectContaining({ description: 'COOP PRONTO', externalRef: null })],
        }),
      ),
    );
  });

  it('renders the suggested category as a badge and — when missing', async () => {
    vi.mocked(previewImport).mockResolvedValue(importPreview());
    const user = userEvent.setup();
    renderWithProviders(<StatementImportDialog open onOpenChange={vi.fn()} />);

    await selectAccountAndUpload(user);

    expect(await screen.findByText('Lebensmittel')).toBeInTheDocument();
    expect(screen.getAllByText('—').length).toBeGreaterThan(0);
  });

  it('shows a readable message when the preview fails with 422', async () => {
    vi.mocked(previewImport).mockRejectedValue(new ApiError('Unreadable statement file', 422));
    const user = userEvent.setup();
    renderWithProviders(<StatementImportDialog open onOpenChange={vi.fn()} />);

    await selectAccountAndUpload(user);

    expect(await screen.findByText('Unreadable statement file')).toBeInTheDocument();
  });

  it('maps a 413 to the too-large message', async () => {
    vi.mocked(previewImport).mockRejectedValue(new ApiError('Request failed: 413', 413));
    const user = userEvent.setup();
    renderWithProviders(<StatementImportDialog open onOpenChange={vi.fn()} />);

    await selectAccountAndUpload(user);

    expect(await screen.findByText('The file is larger than 10 MB')).toBeInTheDocument();
  });

  it('drops a rejected file so a later account change does not preview it', async () => {
    const oversized = new File(['x'], 'statement.csv', { type: 'text/csv' });
    Object.defineProperty(oversized, 'size', { value: 11 * 1024 * 1024 });
    const user = userEvent.setup();
    renderWithProviders(<StatementImportDialog open onOpenChange={vi.fn()} />);

    await user.upload(screen.getByLabelText('Choose file'), oversized);
    expect(await screen.findByText('The file is larger than 10 MB')).toBeInTheDocument();

    await user.click(await screen.findByRole('combobox', { name: 'Account' }));
    await user.click(await screen.findByRole('option', { name: 'Privatkonto' }));

    expect(previewImport).not.toHaveBeenCalled();
  });

  it('hints at missing accounts', async () => {
    vi.mocked(accountsApi.getAll).mockResolvedValue([]);
    renderWithProviders(<StatementImportDialog open onOpenChange={vi.fn()} />);

    expect(
      await screen.findByText(/No account yet — create one under/),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to accounts' })).toHaveAttribute(
      'href',
      '/accounts',
    );
  });
});
