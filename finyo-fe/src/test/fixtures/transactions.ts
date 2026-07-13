import type {
  ImportCommitResult,
  ImportPreview,
  ImportPreviewRow,
  Transaction,
  TransactionPage,
} from '@/api/transactions';

export function transaction(overrides: Partial<Transaction> & { id: string }): Transaction {
  return {
    amount: -42.5,
    currency: 'CHF',
    date: '2026-07-03',
    description: 'MIGROS ZUERICH',
    categoryId: null,
    categoryName: null,
    accountId: 'a1',
    accountName: 'UBS Checking',
    source: 'CSV_IMPORT',
    createdAt: '2026-07-03T10:00:00Z',
    updatedAt: '2026-07-03T10:00:00Z',
    ...overrides,
  };
}

export function transactionPage(
  content: Transaction[],
  overrides: Partial<Omit<TransactionPage, 'content'>> = {},
): TransactionPage {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    size: 50,
    number: 0,
    ...overrides,
  };
}

export function importPreviewRow(
  overrides: Partial<ImportPreviewRow> & { index: number },
): ImportPreviewRow {
  return {
    date: '2026-07-03',
    amount: -42.5,
    currency: 'CHF',
    description: 'MIGROS ZUERICH',
    counterparty: null,
    externalRef: null,
    duplicate: false,
    suggestedCategoryId: null,
    suggestedCategoryName: null,
    ...overrides,
  };
}

export function importPreview(overrides: Partial<ImportPreview> = {}): ImportPreview {
  const rows = overrides.rows ?? [
    importPreviewRow({ index: 0, suggestedCategoryId: 'c1', suggestedCategoryName: 'Lebensmittel' }),
    importPreviewRow({ index: 1, description: 'SBB EASYRIDE', amount: -8.4, externalRef: 'ref-1' }),
    importPreviewRow({ index: 2, description: 'COOP PRONTO', duplicate: true }),
  ];
  return {
    format: 'CSV',
    totalRows: rows.length,
    newRows: rows.filter((row) => !row.duplicate).length,
    duplicates: rows.filter((row) => row.duplicate).length,
    failed: 0,
    errors: [],
    ...overrides,
    rows,
  };
}

export function importCommitResult(
  overrides: Partial<ImportCommitResult> = {},
): ImportCommitResult {
  return {
    totalRows: 2,
    imported: 2,
    skippedDuplicates: 0,
    failed: 0,
    errors: [],
    ...overrides,
  };
}
