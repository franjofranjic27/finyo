const BASE_URL = '/api/v1';

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
    throw new Error(detail?.trim() ? detail : `Request failed: ${response.status}`);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
