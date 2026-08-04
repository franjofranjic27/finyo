import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './auth/AuthProvider';
import { RequireAuth } from './auth/RequireAuth';
import { RequireRole } from './auth/RequireRole';
import { OnboardingGate } from './auth/OnboardingGate';
import { ThemeProvider } from './hooks/useTheme';
import { AppErrorBoundary } from './components/AppErrorBoundary';
import { AppLayout } from './components/layout/AppLayout';
import { OnboardingPage } from './pages/onboarding/OnboardingPage';
import { DashboardPage } from './pages/dashboard/DashboardPage';
import { WealthPage } from './pages/wealth/WealthPage';
import { BudgetPage } from './pages/budget/BudgetPage';
import { AccountsPage } from './pages/accounts/AccountsPage';
import { PortfolioPage } from './pages/investments/PortfolioPage';
import { PositionDetailPage } from './pages/investments/PositionDetailPage';
import { TaxPage } from './pages/tax/TaxPage';
import { DocumentInboxPage } from './pages/documents/DocumentInboxPage';
import { Pillar3Page } from './pages/pillar3/Pillar3Page';
import { Insurance } from './pages/Insurance';
import { Settings } from './pages/Settings';
import { Pillar3ProductsAdminPage } from './pages/admin/Pillar3ProductsAdminPage';
import './i18n/index';
import './index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppErrorBoundary>
      <AuthProvider>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <BrowserRouter>
            <RequireAuth>
              <OnboardingGate>
                <Routes>
                  {/* Onboarding lives outside the AppLayout (minimal centered layout). */}
                  <Route path="/onboarding" element={<OnboardingPage />} />
                  <Route element={<AppLayout />}>
                    <Route path="/" element={<Navigate to="/dashboard" replace />} />
                    <Route path="/dashboard" element={<DashboardPage />} />
                    <Route path="/wealth" element={<WealthPage />} />
                    <Route path="/budget" element={<BudgetPage />} />
                    <Route path="/accounts" element={<AccountsPage />} />
                    <Route
                      path="/investments/positions/:positionId"
                      element={<PositionDetailPage />}
                    />
                    <Route path="/investments" element={<PortfolioPage />} />
                    <Route path="/tax" element={<TaxPage />} />
                    <Route path="/tax/:year" element={<TaxPage />} />
                    <Route path="/documents" element={<DocumentInboxPage />} />
                    <Route path="/pillar3" element={<Pillar3Page />} />
                    <Route path="/insurance" element={<Insurance />} />
                    <Route path="/settings" element={<Settings />} />
                    <Route
                      path="/admin/pillar3-products"
                      element={
                        <RequireRole role="admin">
                          <Pillar3ProductsAdminPage />
                        </RequireRole>
                      }
                    />
                  </Route>
                </Routes>
              </OnboardingGate>
            </RequireAuth>
          </BrowserRouter>
          </ThemeProvider>
        </QueryClientProvider>
      </AuthProvider>
    </AppErrorBoundary>
  </React.StrictMode>
);
