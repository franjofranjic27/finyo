import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './auth/AuthProvider';
import { RequireAuth } from './auth/RequireAuth';
import { AppLayout } from './components/layout/AppLayout';
import { Dashboard } from './pages/Dashboard';
import { Investments } from './pages/Investments';
import { TaxPage } from './pages/tax/TaxPage';
import { Pillar3 } from './pages/Pillar3';
import { Insurance } from './pages/Insurance';
import { Settings } from './pages/Settings';
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
                <Route path="/investments" element={<Investments />} />
                <Route path="/tax" element={<TaxPage />} />
                <Route path="/tax/:year" element={<TaxPage />} />
                <Route path="/pillar3" element={<Pillar3 />} />
                <Route path="/insurance" element={<Insurance />} />
                <Route path="/settings" element={<Settings />} />
              </Route>
            </Routes>
          </RequireAuth>
        </BrowserRouter>
      </QueryClientProvider>
    </AuthProvider>
  </React.StrictMode>
);
