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
  /** Current server version (for sync resolution) */
  serverVersion?: number;
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
