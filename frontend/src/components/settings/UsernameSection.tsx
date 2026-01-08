import { useState, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { useAuthQuery } from '@/hooks/useAuthQuery'
import { useAssociationsQuery, useUpdateKeywordMutation } from '@/hooks/useUserSettingsQuery'
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
import { ChevronDown } from 'lucide-react'

/**
 * Inner component that uses Suspense hooks.
 * Must be wrapped in Suspense boundary.
 */
function UsernameSectionContent() {
  const { t } = useTranslation()
  const { data: user } = useAuthQuery()
  const { associations } = useAssociationsQuery()
  const updateKeyword = useUpdateKeywordMutation()

  // Local state for preview - initialized from user's current keyword
  const [selectedKeyword, setSelectedKeyword] = useState<string | null>(null)

  // The effective keyword: local selection or user's current
  const effectiveKeyword = selectedKeyword ?? user?.usernameKeyword ?? ''

  // Find display name for effective keyword
  const selectedAssociation = associations.find(a => a.keyword === effectiveKeyword)
  const displayName = selectedAssociation?.displayName ?? effectiveKeyword

  // Save keyword to server
  const handleSave = () => {
    if (!selectedKeyword) return

    updateKeyword.mutate(
      { keyword: selectedKeyword },
      {
        onSuccess: () => {
          toast.success(t('settings.username.saveSuccess', 'Username updated'))
          setSelectedKeyword(null) // Reset to sync with server state
        },
        onError: () => {
          toast.error(t('settings.username.saveError', 'Failed to update username'))
        },
      }
    )
  }

  // Check if save should be enabled
  const isSaveEnabled = selectedKeyword !== null && selectedKeyword !== user?.usernameKeyword

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
        toast.error('Popup blocked. Please allow popups for this site.')
        window.location.href = authUrl
      }
    } catch (error) {
      console.error('Failed to initiate OAuth flow:', error)
      toast.error('Failed to start login. Please try again.')
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
        <Button onClick={handleGoogleLogin}>
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
        {t('settings.username.current', 'Current')}: {t('association.sinner')}-{t(`association.${user.usernameKeyword}`, { defaultValue: user.usernameKeyword })}-{user.usernameSuffix}
      </div>

      {/* Keyword dropdown */}
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
              value={effectiveKeyword}
              onValueChange={setSelectedKeyword}
            >
              {associations.map((assoc) => (
                <DropdownMenuRadioItem key={assoc.keyword} value={assoc.keyword}>
                  {assoc.displayName}
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuContent>
        </DropdownMenu>

        <Button
          onClick={handleSave}
          disabled={!isSaveEnabled || updateKeyword.isPending}
        >
          {updateKeyword.isPending
            ? t('settings.username.saving', 'Saving...')
            : t('settings.username.save', 'Save')}
        </Button>
      </div>

      {/* Live preview when changed */}
      {isSaveEnabled && (
        <div className="text-sm">
          {t('settings.username.preview', 'Preview')}: {t('association.sinner')}-{t(`association.${selectedKeyword}`, { defaultValue: selectedKeyword })}-{user.usernameSuffix}
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
