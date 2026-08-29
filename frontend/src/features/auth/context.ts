import { createContext } from 'react';

import type { LoginRequest, RegisterRequest, User } from '../../types/auth';

export type AuthContextValue = {
  token: string | null;
  currentUser: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => void;
  refreshSession: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
