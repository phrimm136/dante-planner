import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useEffect } from 'react'
import { Languages, Settings, User, LogOut } from 'lucide-react'

import { useAuthQuery, useLogout, useLogin } from '@/hooks/useAuthQuery'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from '@/components/ui/dropdown-menu'
import { HeaderNav } from '@/components/HeaderNav'
import { env } from '@/lib/env'
import { toast } from 'sonner'
import {
  generateState,
  generateCodeVerifier,
  generateCodeChallenge,
  storeOAuthParams,
} from '@/lib/oauth'

/**
 * Header component with two-section layout:
 * - Left: Clickable title + Desktop navigation (dropdown menus)
 * - Right: Mobile menu button + Settings buttons (Language, Settings, Sign In)
 *
 * Navigation structure:
 * - Database: Identity, EGO, EGO Gifts
 * - Planner: Mirror Dungeon
 *
 * Note: Background and border styling provided by GlobalLayout wrapper.
 */
export function Header() {
  const { t, i18n } = useTranslation()
  const { data: user } = useAuthQuery()
  const logout = useLogout()
  const login = useLogin()

  const changeLanguage = (lng: string) => {
    i18n.changeLanguage(lng)
  }

  // Listen for OAuth popup messages
  useEffect(() => {
    const handleMessage = async (event: MessageEvent) => {
      // Security: Verify origin
      if (event.origin !== window.location.origin) {
        console.warn('Rejected message from unauthorized origin:', event.origin);
        return;
      }

      // Security: Validate message structure
      if (!event.data || typeof event.data !== 'object') {
        return;
      }

      // Handle OAuth callback from popup
      if (event.data.type === 'GOOGLE_AUTH_SUCCESS') {
        const { code, codeVerifier } = event.data;

        // Validate required fields
        if (!code || typeof code !== 'string') {
          console.error('Invalid auth code format');
          toast.error('Authentication failed: Invalid response');
          return;
        }

        if (!codeVerifier || typeof codeVerifier !== 'string') {
          console.error('Missing code verifier');
          toast.error('Authentication failed: Security validation failed');
          return;
        }

        try {
          await login.mutateAsync({ code, codeVerifier });
          toast.success('Successfully logged in!');
        } catch (error) {
          console.error('Login failed:', error);
          toast.error('Login failed. Please try again.');
        }
      }
    };

    window.addEventListener('message', handleMessage);
    return () => { window.removeEventListener('message', handleMessage); };
  }, [login]);

  const handleGoogleLogin = async () => {
    try {
      // Generate CSRF protection state
      const state = generateState();

      // Generate PKCE parameters
      const codeVerifier = generateCodeVerifier();
      const codeChallenge = await generateCodeChallenge(codeVerifier);

      // Store in memory (SSR-safe)
      storeOAuthParams(state, codeVerifier);

      // Build OAuth URL with state and PKCE
      const clientId = env.VITE_GOOGLE_CLIENT_ID;
      const redirectUri = `${window.location.origin}/auth/callback/google`;
      const scope = 'openid email';
      const authUrl =
        `https://accounts.google.com/o/oauth2/v2/auth?` +
        `client_id=${clientId}&` +
        `redirect_uri=${encodeURIComponent(redirectUri)}&` +
        `response_type=code&` +
        `scope=${encodeURIComponent(scope)}&` +
        `state=${state}&` +
        `code_challenge=${codeChallenge}&` +
        `code_challenge_method=S256`;

      // Open OAuth in popup
      const width = 500;
      const height = 600;
      const left = (window.screen.width - width) / 2;
      const top = (window.screen.height - height) / 2;

      const popup = window.open(
        authUrl,
        'Google Sign-In',
        `width=${width},height=${height},left=${left},top=${top},popup=yes`
      );

      // Detect popup blocking
      if (!popup || popup.closed || typeof popup.closed === 'undefined') {
        toast.error('Popup blocked. Please allow popups for this site.');
        // Fallback: Full-page redirect
        console.warn('Popup blocked, falling back to full-page redirect');
        window.location.href = authUrl;
      }
    } catch (error) {
      console.error('Failed to initiate OAuth flow:', error);
      toast.error('Failed to start login. Please try again.');
    }
  };

  return (
    <header className="px-6 py-4">
      <div className="flex items-center justify-between gap-4">
        {/* Left Section: Title/Logo + Desktop Navigation */}
        <div className="flex items-center shrink-0">
          <Link
            to="/"
            className="text-2xl font-bold text-foreground no-underline hover:text-primary transition-colors"
          >
            Dante's Planner
          </Link>
          <HeaderNav.Desktop />
        </div>

        {/* Right Section: Mobile Menu + Settings Buttons */}
        <div className="shrink-0 flex items-center gap-2">
          <HeaderNav.Mobile />
          {/* Language Selector Dropdown */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                aria-label={t('header.settings.language')}
              >
                <Languages />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuRadioGroup
                value={i18n.language}
                onValueChange={changeLanguage}
              >
                <DropdownMenuRadioItem value="EN">
                  English
                </DropdownMenuRadioItem>
                <DropdownMenuRadioItem value="JP">
                  日本語
                </DropdownMenuRadioItem>
                <DropdownMenuRadioItem value="KR">
                  한국어
                </DropdownMenuRadioItem>
                <DropdownMenuRadioItem value="CN">
                  中文
                </DropdownMenuRadioItem>
              </DropdownMenuRadioGroup>
            </DropdownMenuContent>
          </DropdownMenu>

          {/* TODO: Link to settings page when created */}
          <Button
            variant="ghost"
            size="icon"
            aria-label={t('header.settings.settings')}
          >
            <Settings />
          </Button>

          {/* User Authentication Dropdown */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                aria-label={t('header.settings.signIn')}
              >
                <User />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56">
              {!user ? (
                <>
                  <div className="px-2 py-1.5 text-sm font-semibold">
                    {t('header.auth.welcome')}
                  </div>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <button
                      className="w-full cursor-pointer"
                      onClick={handleGoogleLogin}
                    >
                      {t('header.auth.googleLogin')}
                    </button>
                  </DropdownMenuItem>
                  <DropdownMenuItem asChild>
                    <button
                      className="w-full cursor-pointer"
                      onClick={() => {
                        alert('Apple Sign-In not yet implemented');
                      }}
                    >
                      {t('header.auth.appleLogin')}
                    </button>
                  </DropdownMenuItem>
                </>
              ) : (
                <>
                  <div className="px-2 py-1.5">
                    <p className="text-xs text-muted-foreground">{user.email}</p>
                  </div>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <button
                      className="w-full cursor-pointer flex items-center gap-2"
                      onClick={() => { logout.mutate(); }}
                    >
                      <LogOut className="h-4 w-4" />
                      {t('header.auth.logout')}
                    </button>
                  </DropdownMenuItem>
                </>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </header>
  )
}
