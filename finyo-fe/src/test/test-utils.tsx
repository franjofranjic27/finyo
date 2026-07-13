import type { ReactElement } from 'react';
import { render, type RenderResult } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@/hooks/useTheme';

export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
}

interface ProviderRenderOptions {
  /** Initial URL for the MemoryRouter (default `/`). */
  route?: string;
  /** When set, the UI is mounted under this route path (enables useParams). */
  path?: string;
  queryClient?: QueryClient;
}

/**
 * Renders a component inside the app providers: react-query + theme + MemoryRouter.
 * i18n is a global singleton and is initialised in the test setup file.
 * Auth is intentionally NOT provided here — mock `@/auth/useAuth` locally.
 */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', path, queryClient = createTestQueryClient() }: ProviderRenderOptions = {},
): RenderResult & { queryClient: QueryClient } {
  const tree = path ? (
    <Routes>
      <Route path={path} element={ui} />
    </Routes>
  ) : (
    ui
  );

  const result = render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <MemoryRouter initialEntries={[route]}>{tree}</MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );

  return { ...result, queryClient };
}

/** Builds an unsigned JWT with the given payload — enough for getRealmRoles. */
export function makeAccessToken(payload: object): string {
  const base64 = btoa(JSON.stringify(payload))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `header.${base64}.signature`;
}
