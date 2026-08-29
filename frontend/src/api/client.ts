import type { ApiError } from '../types/auth';

const DEFAULT_API_BASE_URL = 'http://localhost:8080';

export function getApiBaseUrl(): string {
  const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL;

  if (typeof configuredBaseUrl === 'string' && configuredBaseUrl.trim().length > 0) {
    return configuredBaseUrl.replace(/\/$/, '');
  }

  return DEFAULT_API_BASE_URL;
}

export type ApiRequestOptions = RequestInit & {
  token?: string | null;
};

function normalizeApiError(response: Response, payload: unknown): ApiError {
  const errorPayload = (payload ?? {}) as Partial<ApiError> & {
    fieldErrors?: Record<string, string>;
  };

  return {
    timestamp: errorPayload.timestamp ?? new Date().toISOString(),
    status: errorPayload.status ?? response.status,
    error: errorPayload.error ?? response.statusText ?? 'Request failed',
    message: errorPayload.message ?? 'Request failed',
    fieldErrors: errorPayload.fieldErrors ?? {},
  };
}

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
  tokenOverride?: string | null,
): Promise<T> {
  const token = tokenOverride ?? options.token ?? null;
  const headers = new Headers(options.headers ?? {});

  headers.set('Accept', 'application/json');

  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  try {
    const response = await fetch(`${getApiBaseUrl()}${path}`, {
      ...options,
      headers,
    });

    const contentType = response.headers.get('content-type') ?? '';
    const responseText = await response.text();
    const parsedBody =
      responseText && contentType.includes('application/json')
        ? (JSON.parse(responseText) as unknown)
        : responseText
          ? (responseText as unknown)
          : null;

    if (!response.ok) {
      throw normalizeApiError(response, parsedBody);
    }

    if (responseText === '') {
      return undefined as T;
    }

    return parsedBody as T;
  } catch (error) {
    if (error instanceof Error && 'status' in error && typeof error.status === 'number') {
      throw error;
    }

    throw {
      timestamp: new Date().toISOString(),
      status: 0,
      error: 'NetworkError',
      message: 'Unable to connect to the OpsPilot API.',
      fieldErrors: {},
    } satisfies ApiError;
  }
}
