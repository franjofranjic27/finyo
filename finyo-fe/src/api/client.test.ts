import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiRequest } from './client';

function jsonResponse(body: unknown, init: { status?: number; statusText?: string } = {}) {
  const status = init.status ?? 200;
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: init.statusText ?? '',
    json: () => Promise.resolve(body),
  } as Response;
}

function failingJsonResponse(status: number, statusText: string) {
  return {
    ok: false,
    status,
    statusText,
    json: () => Promise.reject(new Error('no body')),
  } as unknown as Response;
}

describe('apiRequest', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('prefixes the path with /api/v1 and returns the parsed JSON body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: '1' }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await apiRequest<{ id: string }>('/accounts');

    expect(result).toEqual({ id: '1' });
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/accounts', expect.any(Object));
  });

  it('sends the bearer token and content type headers', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    await apiRequest('/accounts', {}, 'my-token');

    const [, options] = fetchMock.mock.calls[0];
    expect(options.headers).toMatchObject({
      'Content-Type': 'application/json',
      Authorization: 'Bearer my-token',
    });
  });

  it('omits the Authorization header without a token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    await apiRequest('/accounts');

    const [, options] = fetchMock.mock.calls[0];
    expect(options.headers).not.toHaveProperty('Authorization');
  });

  it('passes method and body through to fetch', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}));
    vi.stubGlobal('fetch', fetchMock);

    await apiRequest('/accounts', { method: 'POST', body: '{"name":"x"}' });

    const [, options] = fetchMock.mock.calls[0];
    expect(options.method).toBe('POST');
    expect(options.body).toBe('{"name":"x"}');
  });

  it('returns undefined for 204 No Content', async () => {
    const response = {
      ok: true,
      status: 204,
      statusText: 'No Content',
      json: () => Promise.reject(new Error('no body')),
    } as unknown as Response;
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));

    await expect(apiRequest<void>('/accounts/1', { method: 'DELETE' })).resolves.toBeUndefined();
  });

  it('throws the problem detail from the error body', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ detail: 'Account not found' }, { status: 404 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiRequest('/accounts/nope')).rejects.toThrow('Account not found');
  });

  it('falls back to the status text when the error body is not JSON', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(failingJsonResponse(500, 'Internal Server Error')),
    );

    await expect(apiRequest('/boom')).rejects.toThrow('Internal Server Error');
  });

  it('falls back to a generic message when the error body has no detail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({}, { status: 502 })));

    await expect(apiRequest('/boom')).rejects.toThrow('Request failed: 502');
  });
});
