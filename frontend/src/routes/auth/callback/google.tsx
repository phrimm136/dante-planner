import { useEffect } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { useAuth } from '@/hooks/useAuth';
import { authService } from '@/services/auth';

export default function GoogleCallback() {
  const navigate = useNavigate();
  const { login } = useAuth();

  useEffect(() => {
    const handleCallback = async () => {
      const params = new URLSearchParams(window.location.search);
      const code = params.get('code');
      const returnPath = sessionStorage.getItem('auth_return_path') || '/';

      if (!code) {
        console.error('No authorization code received');
        navigate({ to: returnPath });
        return;
      }

      try {
        const response = await authService.googleLogin(code);
        login(response.accessToken, response.refreshToken, response.user);
        sessionStorage.removeItem('auth_return_path');
        navigate({ to: returnPath });
      } catch (error) {
        console.error('Google login failed:', error);
        sessionStorage.removeItem('auth_return_path');
        navigate({ to: returnPath });
      }
    };

    handleCallback();
  }, [navigate, login]);

  return (
    <div className="flex items-center justify-center min-h-screen">
      <div className="text-center">
        <p className="text-lg">Completing Google Sign-In...</p>
      </div>
    </div>
  );
}
