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
 * Standard error response from backend (403, 400, etc.)
 */
export interface ApiErrorResponse {
  /** Error code (e.g., USER_BANNED, USER_TIMED_OUT, PLANNER_FORBIDDEN) */
  code: string;
  /** Human-readable error message */
  message: string;
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

/**
 * Custom error class for 404 Not Found responses
 * Enables typed error handling with instanceof checks
 */
export class NotFoundError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'NotFoundError';
  }
}

/**
 * Custom error class for 403 USER_BANNED responses
 * User account has been permanently banned
 */
export class BannedError extends Error {
  readonly code = 'USER_BANNED';

  constructor(message: string) {
    super(message);
    this.name = 'BannedError';
  }
}

/**
 * Custom error class for 403 USER_TIMED_OUT responses
 * User account is temporarily restricted
 */
export class TimedOutError extends Error {
  readonly code = 'USER_TIMED_OUT';

  constructor(message: string) {
    super(message);
    this.name = 'TimedOutError';
  }
}

/**
 * Custom error class for 403 Forbidden with error code
 * Used for PLANNER_FORBIDDEN, COMMENT_FORBIDDEN, etc.
 */
export class ForbiddenError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'ForbiddenError';
    this.code = code;
  }
}

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

    // Backend handles token refresh automatically via JwtAuthenticationFilter
    // If we get 401, auth has genuinely failed (no valid refresh token)
    if (response.status === 401) {
      queryClient.setQueryData(['auth', 'me'], null);
      throw new Error(`HTTP error! status: 401`);
    }

    // Handle 403 Forbidden with typed errors based on error code
    if (response.status === 403) {
      try {
        const errorBody = (await response.json()) as ApiErrorResponse;
        if (errorBody.code === 'USER_BANNED') {
          throw new BannedError(errorBody.message);
        }
        if (errorBody.code === 'USER_TIMED_OUT') {
          throw new TimedOutError(errorBody.message);
        }
        // Other 403 errors (PLANNER_FORBIDDEN, COMMENT_FORBIDDEN, etc.)
        throw new ForbiddenError(errorBody.code, errorBody.message);
      } catch (error) {
        // If error is already one of our custom types, re-throw it
        if (error instanceof BannedError || error instanceof TimedOutError || error instanceof ForbiddenError) {
          throw error;
        }
        // Body parsing failed, throw generic 403
        throw new Error('Forbidden');
      }
    }

    // Handle 404 Not Found with typed error
    if (response.status === 404) {
      throw new NotFoundError('Resource not found');
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
