import { useState, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { useAuthQuery } from '@/hooks/useAuthQuery'
import { useEpithetsQuery, useUpdateEpithetMutation } from '@/hooks/useUserSettingsQuery'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import {
  generateState,
  generateCodeVerifier,
  generateCodeChallenge,
  storeOAuthParams,
} from '@/lib/oauth'
import { env } from '@/lib/env'
import { formatUsername } from '@/lib/formatUsername'
import { ChevronDown } from 'lucide-react'
import { GoogleIcon } from '@/components/icons/GoogleIcon'

/**
 * Inner component that uses Suspense hooks.
 * Must be wrapped in Suspense boundary.
 */
function UsernameSectionContent() {
  const { t } = useTranslation(['common', 'epithet'])
  const { data: user } = useAuthQuery()
  const { epithets } = useEpithetsQuery()
  const updateEpithet = useUpdateEpithetMutation()

  // Local state for preview - initialized from user's current epithet
  const [selectedEpithet, setSelectedEpithet] = useState<string | null>(null)

  // The effective epithet: local selection or user's current
  const effectiveEpithet = selectedEpithet ?? user?.usernameEpithet ?? ''

  // Get translated display name for effective epithet
  const displayName = t(effectiveEpithet, { ns: 'epithet', defaultValue: effectiveEpithet })

  // Save epithet to server
  const handleSave = () => {
    if (!selectedEpithet) return

    updateEpithet.mutate(
      { epithet: selectedEpithet },
      {
        onSuccess: () => {
          toast.success(t('settings.username.saveSuccess', 'Username updated'))
          setSelectedEpithet(null) // Reset to sync with server state
        },
        onError: () => {
          toast.error(t('settings.username.saveError', 'Failed to update username'))
        },
      }
    )
  }

  // Check if save should be enabled
  const isSaveEnabled = selectedEpithet !== null && selectedEpithet !== user?.usernameEpithet

  // OAuth login handler (reused from Header)
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
        toast.error(t('header.auth.popupBlocked', 'Popup blocked. Please allow popups for this site.'))
        window.location.href = authUrl
      }
    } catch (error) {
      console.error('Failed to initiate OAuth flow:', error)
      toast.error(t('header.auth.loginFailed', 'Failed to start login. Please try again.'))
    }
  }

  // Unauthenticated state - show sign-in prompt
  if (!user) {
    return (
      <div className="space-y-4">
        <h2 className="text-lg font-semibold">{t('settings.username.title', 'Username')}</h2>
        <p className="text-muted-foreground">
          {t('settings.username.signInPrompt', 'Sign in to customize your username')}
        </p>
        <Button onClick={handleGoogleLogin} className="flex items-center gap-2">
          <GoogleIcon className="h-4 w-4" />
          {t('header.auth.googleLogin', 'Sign in with Google')}
        </Button>
      </div>
    )
  }

  // Authenticated state - show dropdown and preview
  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">{t('settings.username.title', 'Username')}</h2>

      {/* Current username preview */}
      <div className="text-sm text-muted-foreground">
        {t('settings.username.current')}: {formatUsername(user.usernameEpithet, user.usernameSuffix)}
      </div>

      {/* Epithet dropdown */}
      <div className="flex items-center gap-4">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" className="w-48 justify-between">
              <span>{displayName}</span>
              <ChevronDown className="size-4 opacity-50" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent className="w-48">
            <DropdownMenuRadioGroup
              value={effectiveEpithet}
              onValueChange={setSelectedEpithet}
            >
              {epithets.map((epithet) => (
                <DropdownMenuRadioItem key={epithet} value={epithet}>
                  {t(epithet, { ns: 'epithet', defaultValue: epithet })}
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuContent>
        </DropdownMenu>

        <Button
          onClick={handleSave}
          disabled={!isSaveEnabled || updateEpithet.isPending}
        >
          {updateEpithet.isPending
            ? t('settings.username.saving', 'Saving...')
            : t('settings.username.save', 'Save')}
        </Button>
      </div>

      {/* Live preview when changed */}
      {isSaveEnabled && selectedEpithet && (
        <div className="text-sm">
          {t('settings.username.preview')}: {formatUsername(selectedEpithet, user.usernameSuffix)}
        </div>
      )}
    </div>
  )
}

/**
 * Username section with Suspense boundary.
 * Public component for use in SettingsPage.
 */
export function UsernameSection() {
  return (
    <Suspense fallback={<UsernameSectionSkeleton />}>
      <UsernameSectionContent />
    </Suspense>
  )
}

/**
 * Loading skeleton for username section.
 */
function UsernameSectionSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-6 w-24" />
      <Skeleton className="h-4 w-48" />
      <div className="flex items-center gap-4">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-10 w-20" />
      </div>
    </div>
  )
}
