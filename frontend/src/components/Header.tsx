import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Suspense, useEffect, useState } from 'react'
import { Languages, Settings, User, LogOut, Bell } from 'lucide-react'
import { cn, getDisplayFontForLabel } from '@/lib/utils'
import { formatUsername } from '@/lib/formatUsername'

import { GoogleIcon } from '@/components/icons/GoogleIcon'
import { useAuthQuery, useLogout, useLogin } from '@/hooks/useAuthQuery'
import { useUserSettingsQuery } from '@/hooks/useUserSettings'
import { useFirstLoginStore } from '@/stores/useFirstLoginStore'
import { useUnreadCountQuery } from '@/hooks/useUnreadCountQuery'
import { NotificationDialog } from '@/components/notifications/NotificationDialog'
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
 * UnreadBadge - Renders unread notification count badge on User icon.
 * Must only be rendered when user is authenticated (useSuspenseQuery).
 */
function UnreadBadge() {
  const { unreadCount } = useUnreadCountQuery()
  if (unreadCount === 0) return null
  return (
    <span
      className={cn(
        'absolute -top-1 -right-1 flex items-center justify-center',
        'min-w-5 h-5 px-1 rounded-full',
        'bg-destructive text-destructive-foreground text-xs font-medium'
      )}
    >
      {unreadCount > 99 ? '99+' : unreadCount}
    </span>
  )
}

/**
 * AuthSection - Auth-dependent header section wrapped in Suspense.
 *
 * Contains: notification bell, user dropdown, OAuth handling.
 * Uses useAuthQuery (suspends) - must be wrapped in Suspense boundary.
 */
function AuthSection() {
  const { t, i18n } = useTranslation(['common'])
  const { data: user } = useAuthQuery()
  const logout = useLogout()
  const login = useLogin()
  const { refetch: refetchSettings } = useUserSettingsQuery()
  const openSyncChoiceDialog = useFirstLoginStore((s) => s.openSyncChoiceDialog)

  const [notificationDialogOpen, setNotificationDialogOpen] = useState(false)

  // Listen for OAuth popup messages
  useEffect(() => {
    const handleMessage = async (event: MessageEvent) => {
      if (event.origin !== window.location.origin) {
        console.warn('Rejected message from unauthorized origin:', event.origin)
        return
      }

      if (!event.data || typeof event.data !== 'object') {
        return
      }

      if (event.data.type === 'GOOGLE_AUTH_SUCCESS') {
        const { code, codeVerifier, checkFirstLogin } = event.data

        if (!code || typeof code !== 'string') {
          console.error('Invalid auth code format')
          toast.error(t('header.auth.invalidResponse'))
          return
        }

        if (!codeVerifier || typeof codeVerifier !== 'string') {
          console.error('Missing code verifier')
          toast.error(t('header.auth.securityFailed'))
          return
        }

        try {
          await login.mutateAsync({ code, codeVerifier })
          toast.success(t('header.auth.successLogin'))

          if (checkFirstLogin) {
            const { data: freshSettings } = await refetchSettings()
            if (freshSettings?.syncEnabled === null) {
              openSyncChoiceDialog()
            }
          }
        } catch (error) {
          console.error('Login failed:', error)
          toast.error(t('header.auth.loginFailed'))
        }
      }
    }

    window.addEventListener('message', handleMessage)
    return () => {
      window.removeEventListener('message', handleMessage)
    }
  }, [login, refetchSettings, openSyncChoiceDialog])

  const handleGoogleLogin = async () => {
    try {
      const state = generateState()
      const codeVerifier = generateCodeVerifier()
      const codeChallenge = await generateCodeChallenge(codeVerifier)

      storeOAuthParams(state, codeVerifier)

      const clientId = env.VITE_GOOGLE_CLIENT_ID
      const redirectUri = `${window.location.origin}/auth/callback/google`
      const scope = 'openid email'
      const authUrl =
        `https://accounts.google.com/o/oauth2/v2/auth?` +
        `client_id=${clientId}&` +
        `redirect_uri=${encodeURIComponent(redirectUri)}&` +
        `response_type=code&` +
        `scope=${encodeURIComponent(scope)}&` +
        `state=${state}&` +
        `code_challenge=${codeChallenge}&` +
        `code_challenge_method=S256`

      const width = 500
      const height = 600
      const left = (window.screen.width - width) / 2
      const top = (window.screen.height - height) / 2

      const popup = window.open(
        authUrl,
        'Google Sign-In',
        `width=${width},height=${height},left=${left},top=${top},popup=yes`
      )

      if (!popup || popup.closed || typeof popup.closed === 'undefined') {
        toast.error(t('header.auth.popupBlocked'))
        console.warn('Popup blocked, falling back to full-page redirect')
        window.location.href = authUrl
      }
    } catch (error) {
      console.error('Failed to initiate OAuth flow:', error)
      toast.error(t('header.auth.loginFailed'))
    }
  }

  return (
    <>
      {/* User Authentication Dropdown (with integrated notifications) */}
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            className="relative"
            aria-label={t('header.settings.signIn')}
          >
            <User />
            {user && (
              <Suspense fallback={null}>
                <UnreadBadge />
              </Suspense>
            )}
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
                <Link to="/settings" className="cursor-pointer flex items-center gap-2">
                  <Settings className="h-4 w-4" />
                  {t('header.settings.settings')}
                </Link>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem asChild>
                <button
                  className="w-full cursor-pointer flex items-center gap-2"
                  onClick={handleGoogleLogin}
                >
                  <GoogleIcon className="h-4 w-4" />
                  {t('header.auth.googleLogin')}
                </button>
              </DropdownMenuItem>
            </>
          ) : (
            <>
              <div className="px-2 py-1.5">
                <p className="text-sm font-medium">
                  {formatUsername(user.usernameEpithet, user.usernameSuffix, i18n.language)}
                </p>
                <p className="text-xs text-muted-foreground">{user.email}</p>
              </div>
              <DropdownMenuSeparator />
              <DropdownMenuItem asChild>
                <button
                  className="w-full cursor-pointer flex items-center gap-2"
                  onClick={() => setNotificationDialogOpen(true)}
                >
                  <Bell className="h-4 w-4" />
                  {t('notifications.title')}
                </button>
              </DropdownMenuItem>
              <DropdownMenuItem asChild>
                <Link to="/settings" className="cursor-pointer flex items-center gap-2">
                  <Settings className="h-4 w-4" />
                  {t('header.settings.settings')}
                </Link>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem asChild>
                <button
                  className="w-full cursor-pointer flex items-center gap-2"
                  onClick={() => {
                    logout.mutate(undefined, {
                      onSuccess: () => {
                        toast.success(t('header.auth.successLogout'))
                        window.location.reload()
                      },
                    })
                  }}
                >
                  <LogOut className="h-4 w-4" />
                  {t('header.auth.logout')}
                </button>
              </DropdownMenuItem>
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      {/* Notification dialog - opened from dropdown menu item */}
      {user && (
        <NotificationDialog
          open={notificationDialogOpen}
          onOpenChange={setNotificationDialogOpen}
        />
      )}
    </>
  )
}

/**
 * AuthSectionFallback - Loading state for auth section
 */
function AuthSectionFallback() {
  return (
    <Button variant="ghost" size="icon" disabled>
      <User className="animate-pulse" />
    </Button>
  )
}

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
 * Auth-dependent UI is isolated in AuthSection with Suspense boundary.
 */
export function Header() {
  const { t, i18n } = useTranslation(['common', 'association'])
  const displayFont = getDisplayFontForLabel()

  const changeLanguage = (lng: string) => {
    i18n.changeLanguage(lng)
  }

  return (
    <header className="px-6 py-4">
      <div className="flex items-center justify-between gap-4">
        {/* Left Section: Logo + Title + Desktop Navigation */}
        <div className="flex items-center shrink-0">
          <Link
            to="/"
            className="flex rounded-lg pr-2 items-center gap-2 no-underline group hover:bg-accent transition-colors"
          >
            <img
              src="/images/logo/LCMC.webp"
              alt={t('limbusCompany')}
              className="h-8 w-8"
            />
            <span
              className="text-[22px] text-foreground tracking-tight transition-colors"
              style={{ fontFamily: displayFont }}
            >
              {t('appName')}
            </span>
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

          {/* Auth Section - Suspense boundary isolates auth loading */}
          <Suspense fallback={<AuthSectionFallback />}>
            <AuthSection />
          </Suspense>
        </div>
      </div>
    </header>
  )
}
