import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useTheme } from '@/hooks/useTheme'
import { useAuth } from '@/hooks/useAuth'
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
import { Languages, Sun, Moon, Settings, User, LogOut } from 'lucide-react'

/**
 * Header component with three-section layout:
 * - Left: Clickable title linking to homepage
 * - Center: Navigation menu (In-Game Info, Planner, Community)
 * - Right: Settings buttons (Language, Theme, Settings, Sign In)
 *
 * Note: Background and border styling provided by GlobalLayout wrapper.
 */
export function Header() {
  const { t, i18n } = useTranslation()
  const { theme, toggleTheme } = useTheme()
  const { isAuthenticated, user, logout } = useAuth()

  const changeLanguage = (lng: string) => {
    i18n.changeLanguage(lng)
  }

  return (
    <header className="px-6 py-4">
      <div className="flex items-center justify-between gap-4">
        {/* Left Section: Title/Logo */}
        <div className="shrink-0">
          <Link
            to="/"
            className="text-2xl font-bold text-foreground no-underline hover:text-primary transition-colors"
          >
            Dante's Planner
          </Link>
        </div>

        {/* Center Section: Navigation Links */}
        <nav className="flex items-center gap-6">
          <Button asChild variant="ghost">
            <Link to="/info">{t('header.nav.info')}</Link>
          </Button>
          <Button asChild variant="ghost">
            <Link to="/identity">{t('header.nav.identity')}</Link>
          </Button>
          <Button asChild variant="ghost">
            <Link to="/ego">{t('header.nav.ego')}</Link>
          </Button>
          <Button asChild variant="ghost">
            <Link to="/ego-gift">{t('header.nav.egoGift')}</Link>
          </Button>
          <Button asChild variant="ghost">
            <Link to="/planner">{t('header.nav.planner')}</Link>
          </Button>
          <Button asChild variant="ghost">
            <Link to="/community">{t('header.nav.community')}</Link>
          </Button>
        </nav>

        {/* Right Section: Settings Buttons */}
        <div className="shrink-0 flex items-center gap-2">
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

          {/* Theme Toggle Button */}
          <Button
            variant="ghost"
            size="icon"
            aria-label={t('header.settings.theme')}
            onClick={toggleTheme}
          >
            {theme === 'light' ? <Sun /> : <Moon />}
          </Button>

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
              {!isAuthenticated ? (
                <>
                  <div className="px-2 py-1.5 text-sm font-semibold">
                    {t('header.auth.welcome')}
                  </div>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <button
                      className="w-full cursor-pointer"
                      onClick={() => {
                        sessionStorage.setItem('auth_return_path', window.location.pathname);
                        const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
                        const redirectUri = `${window.location.origin}/auth/callback/google`;
                        const scope = 'openid email profile';
                        const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=code&scope=${scope}`;
                        window.location.href = authUrl;
                      }}
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
                    <p className="text-sm font-semibold">{user?.name}</p>
                    <p className="text-xs text-muted-foreground">{user?.email}</p>
                  </div>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <button
                      className="w-full cursor-pointer flex items-center gap-2"
                      onClick={logout}
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
