import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './auth/AuthProvider';
import { RequireAuth } from './auth/RequireAuth';
import { RequireRole } from './auth/RequireRole';
import { AppLayout } from './components/layout/AppLayout';
import { Dashboard } from './pages/Dashboard';
import { WealthPage } from './pages/wealth/WealthPage';
import { BudgetPage } from './pages/budget/BudgetPage';
import { AccountsPage } from './pages/accounts/AccountsPage';
import { PortfolioPage } from './pages/investments/PortfolioPage';
import { PositionDetailPage } from './pages/investments/PositionDetailPage';
import { TaxPage } from './pages/tax/TaxPage';
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
    <AuthProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <RequireAuth>
            <Routes>
              <Route element={<AppLayout />}>
                <Route path="/" element={<Navigate to="/dashboard" replace />} />
                <Route path="/dashboard" element={<Dashboard />} />
                <Route path="/wealth" element={<WealthPage />} />
                <Route path="/budget" element={<BudgetPage />} />
                <Route path="/accounts" element={<AccountsPage />} />
                <Route path="/investments" element={<PortfolioPage />} />
                <Route path="/investments/positions/:positionId" element={<PositionDetailPage />} />
                <Route path="/tax" element={<TaxPage />} />
                <Route path="/tax/:year" element={<TaxPage />} />
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
          </RequireAuth>
        </BrowserRouter>
      </QueryClientProvider>
    </AuthProvider>
  </React.StrictMode>
);
