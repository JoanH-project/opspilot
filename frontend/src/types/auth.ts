export type User = {
  id: number;
  email: string;
  name: string;
  createdAt: string;
};

export type LoginRequest = {
  email: string;
  password: string;
};

export type RegisterRequest = {
  name: string;
  email: string;
  password: string;
};

export type LoginUserSummary = {
  id: number;
  email: string;
  name: string;
};

export type AuthResponse = {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: LoginUserSummary;
};

export type ApiErrorFieldErrors = Record<string, string>;

export type ApiError = {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  fieldErrors: ApiErrorFieldErrors;
};
