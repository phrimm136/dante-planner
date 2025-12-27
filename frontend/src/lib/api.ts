import { env } from './env';

const API_BASE_URL = env.VITE_API_BASE_URL;

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
}
