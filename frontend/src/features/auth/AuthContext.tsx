import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactElement,
  type ReactNode,
} from 'react';

import { authApi } from '../../api/auth';
import type { LoginRequest, RegisterRequest, User } from '../../types/auth';
import { AuthContext, type AuthContextValue } from './context';
import { getStoredToken, persistToken } from './authStorage';

export function AuthProvider({ children }: { children: ReactNode }): ReactElement {
  const [token, setToken] = useState<string | null>(getStoredToken());
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const logout = useCallback((): void => {
    setToken(null);
    setCurrentUser(null);
    persistToken(null);
  }, []);

  const refreshSession = useCallback(async (): Promise<void> => {
    const storedToken = getStoredToken();

    if (!storedToken) {
      setCurrentUser(null);
      setToken(null);
      persistToken(null);
      setIsLoading(false);
      return;
    }

    try {
      const user = await authApi.getCurrentUser(storedToken);
      setToken(storedToken);
      setCurrentUser(user);
      persistToken(storedToken);
    } catch {
      setToken(null);
      setCurrentUser(null);
      persistToken(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const login = useCallback(async (credentials: LoginRequest): Promise<void> => {
    const response = await authApi.login(credentials);
    const temporaryToken = response.accessToken;

    try {
      const user = await authApi.getCurrentUser(temporaryToken);
      setToken(temporaryToken);
      setCurrentUser(user);
      persistToken(temporaryToken);
    } catch (error) {
      setToken(null);
      setCurrentUser(null);
      persistToken(null);
      throw error;
    }
  }, []);

  const register = useCallback(async (request: RegisterRequest): Promise<void> => {
    await authApi.register(request);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      token,
      currentUser,
      isAuthenticated: Boolean(token && currentUser),
      isLoading,
      login,
      register,
      logout,
      refreshSession,
    }),
    [currentUser, isLoading, login, logout, refreshSession, register, token],
  );

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void refreshSession();
    }, 0);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [refreshSession]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export { AuthContext };
