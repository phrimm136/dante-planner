import { createContext, useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import type { AuthState, User } from '../types/auth';
import { authService } from '../services/auth';

interface AuthContextType extends AuthState {
  login: (accessToken: string, refreshToken: string, user: User) => void;
  logout: () => void;
  checkAuth: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authState, setAuthState] = useState<AuthState>({
    user: null,
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
    isLoading: true,
  });

  const login = (accessToken: string, refreshToken: string, user: User) => {
    authService.saveTokens(accessToken, refreshToken);
    setAuthState({
      user,
      accessToken,
      refreshToken,
      isAuthenticated: true,
      isLoading: false,
    });
  };

  const logout = () => {
    authService.clearTokens();
    setAuthState({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
      isLoading: false,
    });
  };

  const checkAuth = async () => {
    const token = authService.getAccessToken();
    if (!token) {
      setAuthState((prev) => ({ ...prev, isLoading: false }));
      return;
    }

    try {
      const user = await authService.getCurrentUser();
      setAuthState({
        user,
        accessToken: token,
        refreshToken: localStorage.getItem('refreshToken'),
        isAuthenticated: true,
        isLoading: false,
      });
    } catch (error) {
      authService.clearTokens();
      setAuthState({
        user: null,
        accessToken: null,
        refreshToken: null,
        isAuthenticated: false,
        isLoading: false,
      });
    }
  };

  useEffect(() => {
    checkAuth();
  }, []);

  return (
    <AuthContext.Provider value={{ ...authState, login, logout, checkAuth }}>
      {children}
    </AuthContext.Provider>
  );
}
