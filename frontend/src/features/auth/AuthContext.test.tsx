import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { authApi } from '../../api/auth';
import { AuthProvider } from './AuthContext';
import { TOKEN_STORAGE_KEY } from './authStorage';
import { useAuth } from './useAuth';

vi.mock('../../api/auth', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    getCurrentUser: vi.fn(),
  },
}));

function AuthProbe() {
  const { login, isAuthenticated, currentUser, isLoading } = useAuth();
  return (
    <section>
      <output data-testid="loading">{String(isLoading)}</output>
      <output data-testid="authenticated">{String(isAuthenticated)}</output>
      <output data-testid="current-user">{currentUser?.email ?? 'none'}</output>
      <button onClick={() => void login({ email: 'joan@example.com', password: 'password123' }).catch(() => undefined)}>
        Login
      </button>
    </section>
  );
}

describe('AuthProvider login restoration', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.mocked(authApi.login).mockReset();
    vi.mocked(authApi.getCurrentUser).mockReset();
    vi.mocked(authApi.register).mockReset();
  });

  it('does not persist a login token when GET /api/users/me fails', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: 'temporary-login-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: { id: 1, email: 'joan@example.com', name: 'Joan' },
    });
    vi.mocked(authApi.getCurrentUser).mockRejectedValue(new Error('current user request failed'));

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider><AuthProbe /></AuthProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'));
    fireEvent.click(screen.getByRole('button', { name: 'Login' }));

    await waitFor(() => expect(authApi.getCurrentUser).toHaveBeenCalledWith('temporary-login-token'));
    await waitFor(() => expect(screen.getByTestId('authenticated')).toHaveTextContent('false'));
    expect(screen.getByTestId('current-user')).toHaveTextContent('none');
    expect(window.localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
  });
});
