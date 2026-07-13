const BASE_URL = '/api/v1';

/**
 * A non-2xx response. Carries the HTTP status so callers can branch on it
 * (e.g. 409 → "keyword already exists") instead of string-matching the message,
 * which holds the backend's RFC-7807 `detail` and is not translated.
 */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  token?: string
): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: response.statusText }));
    const detail = (error as { detail?: string }).detail;
    // detail can be '' — over HTTP/2 statusText is empty and 401/403 bodies are blank,
    // which used to surface as an invisible, empty error message in the UI.
    throw new ApiError(detail?.trim() ? detail : `Request failed: ${response.status}`, response.status);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
