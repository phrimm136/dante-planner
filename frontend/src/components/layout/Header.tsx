import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Suspense, useState } from 'react'
import { Languages, Settings, User, LogOut, Bell } from 'lucide-react'
import { cn, getDisplayFontForLabel } from '@/lib/utils'
import { getLogoPath } from '@/shared/assets'
import { formatUsername } from '@/lib/formatUsername'

import { GoogleIcon } from '@/components/ui/GoogleIcon'
import { useAuthQuery, useLogout } from '@/shared/auth'
import { useUnreadCountQuery } from '@/shared/notifications'
import { NotificationDialog } from '@/shared/notifications'
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
import { toast } from '@/lib/toast'
import { startGoogleLogin } from '@/shared/auth'

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
        'bg-destructive text-destructive-foreground text-xs font-medium',
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

  const [notificationDialogOpen, setNotificationDialogOpen] = useState(false)

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
              <div className="px-2 py-1.5 text-sm font-semibold">{t('header.auth.welcome')}</div>
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
                  onClick={startGoogleLogin}
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
    void i18n.changeLanguage(lng)
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
            <img src={getLogoPath()} alt={t('limbusCompany')} className="h-8 w-8" />
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
              <Button variant="ghost" size="icon" aria-label={t('header.settings.language')}>
                <Languages />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuRadioGroup value={i18n.language} onValueChange={changeLanguage}>
                <DropdownMenuRadioItem value="EN">English</DropdownMenuRadioItem>
                <DropdownMenuRadioItem value="JP">日本語</DropdownMenuRadioItem>
                <DropdownMenuRadioItem value="KR">한국어</DropdownMenuRadioItem>
                <DropdownMenuRadioItem value="CN">中文</DropdownMenuRadioItem>
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
