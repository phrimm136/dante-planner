import { ApiClient } from '../lib/api';
import type { LoginResponse, User } from '../types/auth';

export const authService = {
  async googleLogin(code: string): Promise<LoginResponse> {
    return ApiClient.post<LoginResponse>('/api/auth/google/callback', {
      code,
      provider: 'google',
    });
  },

  async getCurrentUser(): Promise<User> {
    return ApiClient.get<User>('/api/auth/me');
  },

  saveTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
  },

  clearTokens(): void {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
  },

  getAccessToken(): string | null {
    return localStorage.getItem('accessToken');
  },
};
