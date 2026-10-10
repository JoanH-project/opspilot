import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactElement,
  type ReactNode,
} from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { authApi } from '../../api/auth';
import type { LoginRequest, RegisterRequest, User } from '../../types/auth';
import { AuthContext, type AuthContextValue } from './context';
import { getStoredToken, persistToken } from './authStorage';
import { getStoredWorkspaceId, persistWorkspaceId } from '../workspaces/workspaceStorage';
import { clearRejectedWorkspaces } from '../workspaces/workspaceRecovery';

export function AuthProvider({ children }: { children: ReactNode }): ReactElement {
  const queryClient = useQueryClient();
  const [token, setToken] = useState<string | null>(getStoredToken());
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const sessionGeneration = useRef(0);
  const currentUserId = useRef<number | null>(null);

  const logout = useCallback((): void => {
    const previousUserId = currentUserId.current;
    sessionGeneration.current += 1;
    setToken(null);
    setCurrentUser(null);
    setIsLoading(false);
    currentUserId.current = null;
    persistToken(null);
    persistWorkspaceId(null, previousUserId);
    clearRejectedWorkspaces();
    void queryClient.cancelQueries();
    queryClient.clear();
  }, [queryClient]);

  const refreshSession = useCallback(async (): Promise<void> => {
    const generation = sessionGeneration.current;
    const storedToken = getStoredToken();

    if (!storedToken) {
      const previousUserId = currentUserId.current;
      setCurrentUser(null);
      setToken(null);
      currentUserId.current = null;
      persistToken(null);
      persistWorkspaceId(null, previousUserId);
      clearRejectedWorkspaces();
      void queryClient.cancelQueries();
      queryClient.clear();
      setIsLoading(false);
      return;
    }

    try {
      const user = await authApi.getCurrentUser(storedToken);
      if (
        generation !== sessionGeneration.current ||
        getStoredToken() !== storedToken
      ) {
        return;
      }
      if (currentUserId.current !== null && currentUserId.current !== user.id) {
        void queryClient.cancelQueries();
        queryClient.clear();
        clearRejectedWorkspaces();
      }
      setToken(storedToken);
      setCurrentUser(user);
      currentUserId.current = user.id;
      persistToken(storedToken);
      persistWorkspaceId(getStoredWorkspaceId(user.id), user.id);
    } catch {
      if (generation !== sessionGeneration.current) {
        return;
      }
      const previousUserId = currentUserId.current;
      setToken(null);
      setCurrentUser(null);
      currentUserId.current = null;
      persistToken(null);
      persistWorkspaceId(null, previousUserId);
      clearRejectedWorkspaces();
      void queryClient.cancelQueries();
      queryClient.clear();
    } finally {
      if (generation === sessionGeneration.current) {
        setIsLoading(false);
      }
    }
  }, [queryClient]);

  const login = useCallback(async (credentials: LoginRequest): Promise<void> => {
    const generation = ++sessionGeneration.current;
    const response = await authApi.login(credentials);
    if (generation !== sessionGeneration.current) {
      return;
    }
    const temporaryToken = response.accessToken;

    try {
      const user = await authApi.getCurrentUser(temporaryToken);
      if (generation !== sessionGeneration.current) {
        return;
      }
      if (currentUserId.current !== null && currentUserId.current !== user.id) {
        void queryClient.cancelQueries();
        queryClient.clear();
        clearRejectedWorkspaces();
      }
      setToken(temporaryToken);
      setCurrentUser(user);
      currentUserId.current = user.id;
      persistToken(temporaryToken);
      persistWorkspaceId(getStoredWorkspaceId(user.id), user.id);
    } catch (error) {
      if (generation !== sessionGeneration.current) {
        throw error;
      }
      const previousUserId = currentUserId.current;
      setToken(null);
      setCurrentUser(null);
      currentUserId.current = null;
      persistToken(null);
      persistWorkspaceId(null, previousUserId);
      queryClient.clear();
      throw error;
    }
  }, [queryClient]);

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
