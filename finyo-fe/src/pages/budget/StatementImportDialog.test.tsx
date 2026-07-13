import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StatementImportDialog } from './StatementImportDialog';
import { accountsApi } from '@/api/accounts';
import { previewImport, transactionsApi, TransactionImportError } from '@/api/transactions';
import { renderWithProviders } from '@/test/test-utils';
import { bankAccount } from '@/test/fixtures/accounts';
import { importCommitResult, importPreview } from '@/test/fixtures/transactions';

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

// Keep TransactionImportError real so instanceof checks in the dialog work.
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
      importCommitResult({ total: 1, imported: 1 }),
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

  it('renders the suggested category as a badge and — when missing', async () => {
    vi.mocked(previewImport).mockResolvedValue(importPreview());
    const user = userEvent.setup();
    renderWithProviders(<StatementImportDialog open onOpenChange={vi.fn()} />);

    await selectAccountAndUpload(user);

    expect(await screen.findByText('Lebensmittel')).toBeInTheDocument();
    expect(screen.getAllByText('—').length).toBeGreaterThan(0);
  });

  it('shows a readable message when the preview fails with 422', async () => {
    vi.mocked(previewImport).mockRejectedValue(
      new TransactionImportError('Unreadable statement file', 422),
    );
    const user = userEvent.setup();
    renderWithProviders(<StatementImportDialog open onOpenChange={vi.fn()} />);

    await selectAccountAndUpload(user);

    expect(await screen.findByText('Unreadable statement file')).toBeInTheDocument();
  });

  it('maps a 413 to the too-large message', async () => {
    vi.mocked(previewImport).mockRejectedValue(
      new TransactionImportError('Request failed: 413', 413),
    );
    const user = userEvent.setup();
    renderWithProviders(<StatementImportDialog open onOpenChange={vi.fn()} />);

    await selectAccountAndUpload(user);

    expect(await screen.findByText('The file is larger than 10 MB')).toBeInTheDocument();
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
