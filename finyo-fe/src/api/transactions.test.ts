import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { previewImport, transactionsApi, TransactionImportError } from './transactions';
import type { ImportCommitRequest, TransactionInput } from './transactions';
import { apiRequest } from './client';

vi.mock('./client', () => ({ apiRequest: vi.fn() }));

const apiRequestMock = vi.mocked(apiRequest);
const TOKEN = 'test-token';

describe('transactionsApi', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('getAll defaults to page 0 with 50 rows', () => {
    transactionsApi.getAll(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith('/transactions?page=0&size=50', {}, TOKEN);
  });

  it('getAll passes the date range and page', () => {
    transactionsApi.getAll(TOKEN, { from: '2026-07-01', to: '2026-07-31', page: 2 });
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/transactions?from=2026-07-01&to=2026-07-31&page=2&size=50',
      {},
      TOKEN,
    );
  });

  it('update PUTs the full transaction to the id path', () => {
    const input: TransactionInput = {
      amount: -42.5,
      currency: 'CHF',
      date: '2026-07-03',
      description: 'MIGROS',
      categoryId: 'c1',
      accountId: 'a1',
    };
    transactionsApi.update(TOKEN, 't1', input);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/transactions/t1',
      { method: 'PUT', body: JSON.stringify(input) },
      TOKEN,
    );
  });

  it('commitImport POSTs the confirmed rows', () => {
    const request: ImportCommitRequest = {
      accountId: 'a1',
      format: 'CSV',
      skipDuplicates: true,
      rows: [
        {
          date: '2026-07-03',
          amount: -42.5,
          currency: 'CHF',
          description: 'MIGROS',
          externalRef: null,
          categoryId: 'c1',
        },
      ],
    };
    transactionsApi.commitImport(TOKEN, request);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/transactions/import/commit',
      { method: 'POST', body: JSON.stringify(request) },
      TOKEN,
    );
  });
});

describe('previewImport', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const file = new File(['01.07.2026;-12.50;Migros'], 'statement.csv', { type: 'text/csv' });

  it('POSTs multipart form data with file, account and options', async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ format: 'CSV', rows: [], errors: [] }), { status: 200 }),
    );

    await previewImport(TOKEN, file, 'a1', { format: 'CSV', preset: 'UBS', hasHeader: false });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/transactions/import/preview',
      expect.objectContaining({
        method: 'POST',
        headers: { Authorization: `Bearer ${TOKEN}` },
      }),
    );
    const body = fetchMock.mock.calls[0][1].body as FormData;
    expect(body.get('file')).toBe(file);
    expect(body.get('accountId')).toBe('a1');
    expect(body.get('format')).toBe('CSV');
    expect(body.get('preset')).toBe('UBS');
    expect(body.get('hasHeader')).toBe('false');
  });

  it('throws a TransactionImportError with the ProblemDetail message', async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ detail: 'Datei kann nicht gelesen werden' }), { status: 422 }),
    );

    const error = await previewImport(TOKEN, file, 'a1').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(TransactionImportError);
    expect((error as TransactionImportError).status).toBe(422);
    expect((error as TransactionImportError).message).toBe('Datei kann nicht gelesen werden');
  });

  it('falls back to the status code when the error body is not JSON', async () => {
    fetchMock.mockResolvedValue(new Response('payload too large', { status: 413 }));

    const error = await previewImport(TOKEN, file, 'a1').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(TransactionImportError);
    expect((error as TransactionImportError).status).toBe(413);
    expect((error as TransactionImportError).message).toBe('Request failed: 413');
  });
});
