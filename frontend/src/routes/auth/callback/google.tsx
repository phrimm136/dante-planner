import { useEffect } from 'react';
import { validateAndGetOAuthParams } from '@/lib/oauth';
import { useFirstLoginStore } from '@/stores/useFirstLoginStore';

export default function GoogleCallback() {
  const openSyncChoiceDialog = useFirstLoginStore((s) => s.openSyncChoiceDialog);

  useEffect(() => {
    const handleCallback = () => {
      const params = new URLSearchParams(window.location.search);
      const code = params.get('code');
      const state = params.get('state');

      // Validate authorization code
      if (!code) {
        console.error('No authorization code received');
        window.close();
        return;
      }

      // Validate state parameter (CSRF protection)
      const oauthParams = validateAndGetOAuthParams(state);
      if (!oauthParams) {
        console.error('State validation failed - possible CSRF attack');
        window.close();
        return;
      }

      // Check if opened as popup (has opener)
      if (window.opener && !window.opener.closed) {
        // Send code and code_verifier to parent window via postMessage
        // Include flag to check first-login status
        window.opener.postMessage(
          {
            type: 'GOOGLE_AUTH_SUCCESS',
            code,
            codeVerifier: oauthParams.codeVerifier,
            checkFirstLogin: true,
          },
          window.location.origin
        );
        // Close popup
        window.close();
      } else {
        // Fallback: opened as full page redirect (shouldn't happen with popup)
        console.error('OAuth callback opened without popup context');
        window.location.href = '/';
      }
    };

    handleCallback();
  }, [openSyncChoiceDialog]);

  return (
    <div className="flex items-center justify-center min-h-screen">
      <div className="text-center">
        <p className="text-lg">Completing Google Sign-In...</p>
      </div>
    </div>
  );
}
