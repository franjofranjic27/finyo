import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { positionDetailApi } from './positionDetail';
import type { UpdateInstrumentRequest, UpdatePositionRequest } from './positionDetail';
import { apiRequest } from './client';
import { positionDetail } from '@/test/fixtures/portfolio';

vi.mock('./client', () => ({ apiRequest: vi.fn() }));

const apiRequestMock = vi.mocked(apiRequest);
const TOKEN = 'test-token';

function jsonResponse(body: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    json: () => Promise.resolve(body),
    blob: () => Promise.resolve(new Blob(['%PDF-1.7'], { type: 'application/pdf' })),
  } as unknown as Response;
}

describe('positionDetailApi', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('getPosition requests /positions/{id} with the token', () => {
    positionDetailApi.getPosition(TOKEN, 'p1');
    expect(apiRequestMock).toHaveBeenCalledWith('/positions/p1', {}, TOKEN);
  });

  it('updateInstrument PATCHes the serialised instrument fields', () => {
    const update: UpdateInstrumentRequest = { name: 'Nestlé SA', assetClass: 'STOCK', ter: 0.15 };
    positionDetailApi.updateInstrument(TOKEN, 'p1', update);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/positions/p1/instrument',
      { method: 'PATCH', body: JSON.stringify(update) },
      TOKEN,
    );
  });

  it('updatePosition PATCHes the serialised holding fields', () => {
    const update: UpdatePositionRequest = {
      quantity: 5,
      purchasePrice: 90,
      purchaseDate: null,
      currentPrice: 120,
    };
    positionDetailApi.updatePosition(TOKEN, 'p1', update);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/positions/p1',
      { method: 'PATCH', body: JSON.stringify(update) },
      TOKEN,
    );
  });

  it('deleteFactsheet issues DELETE on the factsheet path', () => {
    positionDetailApi.deleteFactsheet(TOKEN, 'p1');
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/positions/p1/factsheet',
      { method: 'DELETE' },
      TOKEN,
    );
  });

  it('uploadFactsheet POSTs multipart form data without a JSON content type', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(positionDetail({ positionId: 'p1' })));
    vi.stubGlobal('fetch', fetchMock);
    const file = new File(['%PDF-1.7'], 'factsheet.pdf', { type: 'application/pdf' });

    await positionDetailApi.uploadFactsheet(TOKEN, 'p1', file);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/positions/p1/factsheet');
    expect(init.method).toBe('POST');
    // Only the bearer token — the browser must set the multipart boundary itself.
    expect(init.headers).toEqual({ Authorization: `Bearer ${TOKEN}` });
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get('file')).toBe(file);
  });

  it('uploadFactsheet surfaces the ProblemDetail detail on errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ detail: 'File too large' }, false, 413)),
    );
    const file = new File(['x'], 'factsheet.pdf', { type: 'application/pdf' });

    await expect(positionDetailApi.uploadFactsheet(TOKEN, 'p1', file)).rejects.toThrow(
      'File too large',
    );
  });

  it('fetchFactsheet GETs the PDF as a blob with the bearer token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(null));
    vi.stubGlobal('fetch', fetchMock);

    const blob = await positionDetailApi.fetchFactsheet(TOKEN, 'p1');

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/positions/p1/factsheet', {
      headers: { Authorization: `Bearer ${TOKEN}` },
    });
    expect(blob.type).toBe('application/pdf');
  });
});
