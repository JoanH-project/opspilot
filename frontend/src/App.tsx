import type { ReactElement } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';

import './App.css';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AuthProvider } from './features/auth/AuthContext';
import { useAuth } from './features/auth/useAuth';
import { WorkspaceBootstrap } from './features/workspaces/WorkspaceBootstrap';
import { WorkspaceDashboardRoute } from './features/workspaces/WorkspaceDashboardRoute';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { queryClient } from './queryClient';

function AppRoutes(): ReactElement {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return <div className="page-loading">Loading your session…</div>;
  }

  return (
    <Routes>
      <Route path="/" element={<Navigate to={isAuthenticated ? '/app' : '/login'} replace />} />

      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/app" replace /> : <LoginPage />}
      />

      <Route
        path="/register"
        element={isAuthenticated ? <Navigate to="/app" replace /> : <RegisterPage />}
      />

      <Route
        path="/app"
        element={
          <ProtectedRoute>
            <WorkspaceBootstrap />
          </ProtectedRoute>
        }
      />

      <Route
        path="/app/:workspaceId/dashboard"
        element={
          <ProtectedRoute>
            <WorkspaceDashboardRoute />
          </ProtectedRoute>
        }
      />

      <Route path="*" element={<Navigate to={isAuthenticated ? '/app' : '/login'} replace />} />
    </Routes>
  );
}

export default function App(): ReactElement {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
