import { apiRequest } from './client';
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '../types/auth';

export const authApi = {
  register: async (request: RegisterRequest) =>
    apiRequest<User>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(request),
    }),

  login: async (request: LoginRequest) =>
    apiRequest<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(request),
    }),

  getCurrentUser: async (token: string) =>
    apiRequest<User>('/api/users/me', {
      method: 'GET',
    }, token),
};
