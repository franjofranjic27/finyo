import { ApiError, apiRequest } from './client';

// The multipart preview upload bypasses apiRequest on purpose: the shared
// client hardcodes Content-Type application/json, while FormData needs the
// browser-generated multipart boundary (no explicit Content-Type header).
const BASE_URL = '/api/v1';

export type ImportFormat = 'CSV' | 'EXCEL' | 'CAMT053';

/** Bank-specific CSV column presets supported by the backend. */
export type CsvPreset = 'UBS' | 'RAIFFEISEN' | 'POSTFINANCE' | 'CUSTOM';

export interface Transaction {
  id: string;
  amount: number;
  currency: string;
  date: string;
  description: string | null;
  categoryId: string | null;
  categoryName: string | null;
  accountId: string;
  accountName: string;
  source: string;
  createdAt: string;
  updatedAt: string;
}

/** Spring page envelope as flattened by the backend's TransactionPageResponse. */
export interface TransactionPage {
  content: Transaction[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/** Mirrors the backend TransactionRequest — a full replace on PUT. */
export interface TransactionInput {
  amount: number;
  currency: string | null;
  date: string;
  description: string | null;
  categoryId: string | null;
  accountId: string;
}

export interface TransactionListParams {
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface ImportPreviewRow {
  index: number;
  date: string;
  amount: number;
  currency: string;
  description: string;
  counterparty: string | null;
  externalRef: string | null;
  duplicate: boolean;
  suggestedCategoryId: string | null;
  suggestedCategoryName: string | null;
}

export interface ImportPreview {
  format: ImportFormat;
  totalRows: number;
  newRows: number;
  duplicates: number;
  failed: number;
  rows: ImportPreviewRow[];
  errors: string[];
}

export interface ImportPreviewOptions {
  format?: ImportFormat;
  preset?: CsvPreset;
  dateColumn?: number;
  amountColumn?: number;
  descriptionColumn?: number;
  dateFormat?: string;
  hasHeader?: boolean;
}

export interface ImportCommitRow {
  date: string;
  amount: number;
  currency: string;
  description: string;
  externalRef: string | null;
  categoryId: string | null;
}

export interface ImportCommitRequest {
  accountId: string;
  format: ImportFormat;
  skipDuplicates: boolean;
  rows: ImportCommitRow[];
}

/** Mirrors the backend ImportResultResponse — field names are the backend's. */
export interface ImportCommitResult {
  totalRows: number;
  imported: number;
  skippedDuplicates: number;
  failed: number;
  errors: string[];
}

function listQuery({ from, to, page = 0, size = 50 }: TransactionListParams): string {
  const params = new URLSearchParams();
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  params.set('page', String(page));
  params.set('size', String(size));
  return params.toString();
}

export const transactionsApi = {
  getAll: (token: string, params: TransactionListParams = {}) =>
    apiRequest<TransactionPage>(`/transactions?${listQuery(params)}`, {}, token),

  update: (token: string, id: string, data: TransactionInput) =>
    apiRequest<Transaction>(
      `/transactions/${id}`,
      { method: 'PUT', body: JSON.stringify(data) },
      token,
    ),

  commitImport: (token: string, data: ImportCommitRequest) =>
    apiRequest<ImportCommitResult>(
      '/transactions/import/commit',
      { method: 'POST', body: JSON.stringify(data) },
      token,
    ),
};

/**
 * Uploads a bank statement for a dry-run parse. Nothing is persisted —
 * the caller confirms the previewed rows via `transactionsApi.commitImport`.
 *
 * @throws ApiError on any non-2xx response (413 too large, 422 unreadable file, …)
 */
export async function previewImport(
  token: string,
  file: File,
  accountId: string,
  options: ImportPreviewOptions = {},
): Promise<ImportPreview> {
  const body = new FormData();
  body.append('file', file);
  body.append('accountId', accountId);
  for (const [key, value] of Object.entries(options)) {
    if (value !== undefined) body.append(key, String(value));
  }

  const response = await fetch(`${BASE_URL}/transactions/import/preview`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body,
  });

  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
    throw new ApiError(
      problem?.detail?.trim() ? problem.detail : `Request failed: ${response.status}`,
      response.status,
    );
  }

  return response.json() as Promise<ImportPreview>;
}
