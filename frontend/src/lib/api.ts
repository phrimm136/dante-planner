import { env } from './env';
import { queryClient } from './queryClient';

const API_BASE_URL = env.VITE_API_BASE_URL;

/**
 * Error response for version conflicts (HTTP 409)
 * Used for optimistic locking during planner sync
 */
export interface ApiConflictError {
  /** Error code identifying the conflict type */
  code: string;
  /** Human-readable error message */
  message: string;
  /** Current server version (for sync resolution) - REQUIRED for conflict resolution */
  serverVersion: number;
}

/**
 * Custom error class for 409 Conflict responses
 * Enables typed error handling with instanceof checks
 */
export class ConflictError extends Error {
  /** Server's current version for sync resolution */
  readonly serverVersion: number;

  constructor(message: string, serverVersion: number) {
    super(message);
    this.name = 'ConflictError';
    this.serverVersion = serverVersion;
  }
}

let isRefreshing = false;
let refreshPromise: Promise<void> | null = null;

export class ApiClient {
  static async fetch<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options.headers as Record<string, string>),
    };

    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      ...options,
      headers,
      credentials: 'include', // Include HttpOnly cookies
    });

    // Handle 401 by attempting token refresh
    if (response.status === 401) {
      // Parse error body to distinguish expired token vs no token
      let errorCode = 'UNKNOWN';
      try {
        const errorBody = (await response.json()) as { error?: string };
        errorCode = errorBody.error ?? 'UNKNOWN';
      } catch {
        // Body parse failed, treat as unknown
      }

      // TOKEN_EXPIRED = should attempt refresh (unless on refresh/logout)
      const shouldRefresh =
        errorCode === 'TOKEN_EXPIRED' &&
        !endpoint.endsWith('/auth/refresh') &&
        !endpoint.endsWith('/auth/logout');

      if (shouldRefresh) {
        const refreshed = await this.handleUnauthorized();
        if (refreshed) {
          return this.fetch<T>(endpoint, options);
        }
        // Refresh failed - auth state already cleared, throw 401 for caller to handle
      }

      // UNAUTHORIZED = no token (guest), or other auth errors - throw
      throw new Error(`HTTP error! status: 401`);
    }

    // Handle 409 conflict with typed error
    if (response.status === 409) {
      let serverVersion = 1;
      try {
        const errorBody = (await response.json()) as ApiConflictError;
        serverVersion = errorBody.serverVersion ?? 1;
      } catch {
        // Body parsing failed, use default
      }
      throw new ConflictError(
        `Conflict: server version ${serverVersion}`,
        serverVersion
      );
    }

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    // Handle 204 No Content (e.g., logout, DELETE operations)
    if (response.status === 204) {
      return undefined as T;
    }

    return response.json();
  }

  private static async handleUnauthorized(): Promise<boolean> {
    // If already refreshing, wait for that to complete
    if (isRefreshing && refreshPromise) {
      await refreshPromise;
      return true;
    }

    isRefreshing = true;
    let success = false;

    refreshPromise = (async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
          method: 'POST',
          credentials: 'include',
        });

        if (response.ok) {
          success = true;
        } else {
          // Refresh failed - clear auth state so UI shows logged-out
          queryClient.setQueryData(['auth', 'user'], null);
        }
      } finally {
        isRefreshing = false;
        refreshPromise = null;
      }
    })();

    await refreshPromise;
    return success;
  }

  static async get<T>(endpoint: string): Promise<T> {
    return this.fetch<T>(endpoint, { method: 'GET' });
  }

  static async post<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.fetch<T>(endpoint, {
      method: 'POST',
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  static async put<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.fetch<T>(endpoint, {
      method: 'PUT',
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  static async delete<T>(endpoint: string): Promise<T> {
    return this.fetch<T>(endpoint, { method: 'DELETE' });
  }

  static async patch<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.fetch<T>(endpoint, {
      method: 'PATCH',
      body: data ? JSON.stringify(data) : undefined,
    });
  }

  /**
   * Create an EventSource for Server-Sent Events
   * Used for real-time planner sync notifications
   */
  static createEventSource(endpoint: string): EventSource {
    return new EventSource(`${API_BASE_URL}${endpoint}`, {
      withCredentials: true,
    });
  }
}
