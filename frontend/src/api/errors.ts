import type { ApiError } from '../types/auth';

export function isApiError(error: unknown): error is ApiError {
  return (
    typeof error === 'object' &&
    error !== null &&
    'status' in error &&
    typeof error.status === 'number' &&
    'message' in error &&
    typeof error.message === 'string'
  );
}

export function getApiErrorMessage(error: unknown, fallback: string): string {
  return isApiError(error) ? error.message : fallback;
}
