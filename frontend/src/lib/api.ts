import { env } from './env';

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
    if (response.status === 401 && !endpoint.includes('/auth/')) {
      // Avoid infinite loop on auth endpoints
      await this.handleUnauthorized();

      // Retry original request after refresh
      return this.fetch<T>(endpoint, options);
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

    return response.json();
  }

  private static async handleUnauthorized(): Promise<void> {
    // If already refreshing, wait for that to complete
    if (isRefreshing && refreshPromise) {
      await refreshPromise;
      return;
    }

    isRefreshing = true;
    refreshPromise = (async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
          method: 'POST',
          credentials: 'include',
        });

        if (!response.ok) {
          // Refresh failed - redirect to home
          window.location.href = '/';
          throw new Error('Token refresh failed');
        }
      } finally {
        isRefreshing = false;
        refreshPromise = null;
      }
    })();

    await refreshPromise;
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

  static async delete(endpoint: string): Promise<void> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };

    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      method: 'DELETE',
      headers,
      credentials: 'include',
    });

    // Handle 401 by attempting token refresh
    if (response.status === 401 && !endpoint.includes('/auth/')) {
      await this.handleUnauthorized();
      return this.delete(endpoint);
    }

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    // DELETE returns no content (204)
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
