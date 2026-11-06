import { useEffect } from 'react';

import { isApiError } from '../../api/errors';
import { useAuth } from './useAuth';

export function useLogoutOnUnauthorized(error: unknown): void {
  const { logout } = useAuth();

  useEffect(() => {
    if (isApiError(error) && error.status === 401) {
      logout();
    }
  }, [error, logout]);
}
