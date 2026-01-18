import { useState, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'

import { useAuthQuery, authQueryKeys } from '@/hooks/useAuthQuery'
import { useDeleteAccountMutation } from '@/hooks/useUserSettingsQuery'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { AccountDeleteDialog } from './AccountDeleteDialog'
import {
  generateState,
  generateCodeVerifier,
  generateCodeChallenge,
  storeOAuthParams,
} from '@/lib/oauth'
import { env } from '@/lib/env'

/**
 * Inner component that uses Suspense hooks.
 * Must be wrapped in Suspense boundary.
 */
function AccountDeleteSectionContent() {
  const { t, i18n } = useTranslation()
  const { data: user } = useAuthQuery()
  const deleteAccount = useDeleteAccountMutation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [dialogOpen, setDialogOpen] = useState(false)

  // OAuth login handler (reused from UsernameSection pattern)
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

  // Handle account deletion
  const handleDelete = () => {
    deleteAccount.mutate(undefined, {
      onSuccess: (response) => {
        // Format the permanent deletion date with error handling
        let formattedDate = 'unknown date'
        try {
          const date = new Date(response.permanentDeleteAt)
          if (!isNaN(date.getTime())) {
            // Map i18n language to Intl locale
            const localeMap: Record<string, string> = {
              EN: 'en-US',
              JP: 'ja-JP',
              KR: 'ko-KR',
              CN: 'zh-CN',
            }
            const locale = localeMap[i18n.language] ?? 'en-US'
            formattedDate = date.toLocaleDateString(locale, {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
            })
          }
        } catch (error) {
          console.error('Invalid date format:', response.permanentDeleteAt, error)
        }

        // Show success toast
        toast.success(
          t('settings.deleteAccount.success', {
            date: formattedDate,
            days: response.gracePeriodDays ?? 30,
          })
        )

        // Close dialog
        setDialogOpen(false)

        // CRITICAL: Invalidate auth cache IMMEDIATELY to trigger logout
        queryClient.setQueryData(authQueryKeys.me, null)

        // Wait 2 seconds then redirect
        setTimeout(() => {
          navigate({ to: '/' })
        }, 2000)
      },
      onError: () => {
        toast.error(t('settings.deleteAccount.error'))
        // Keep dialog open for retry
      },
    })
  }


  // Unauthenticated state - show sign-in prompt
  if (!user) {
    return (
      <div className="space-y-4">
        <h2 className="text-lg font-semibold">{t('settings.deleteAccount.title')}</h2>
        <p className="text-muted-foreground">
          {t('settings.deleteAccount.signInPrompt')}
        </p>
        <Button onClick={handleGoogleLogin}>
          {t('header.auth.googleLogin')}
        </Button>
      </div>
    )
  }

  // Authenticated state - show delete button
  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">{t('settings.deleteAccount.title')}</h2>
      <p className="text-sm text-muted-foreground">
        {t('settings.deleteAccount.warning')}
      </p>
      <Button
        variant="destructive"
        onClick={() => setDialogOpen(true)}
      >
        {t('settings.deleteAccount.title')}
      </Button>

      <AccountDeleteDialog
        open={dialogOpen}
        onConfirm={handleDelete}
        onCancel={() => setDialogOpen(false)}
        isPending={deleteAccount.isPending}
      />
    </div>
  )
}

/**
 * Account deletion section with Suspense boundary.
 * Public component for use in SettingsPage.
 */
export function AccountDeleteSection() {
  return (
    <Suspense fallback={<AccountDeleteSectionSkeleton />}>
      <AccountDeleteSectionContent />
    </Suspense>
  )
}

/**
 * Loading skeleton for account delete section.
 */
function AccountDeleteSectionSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-6 w-32" />
      <Skeleton className="h-4 w-64" />
      <Skeleton className="h-10 w-32" />
    </div>
  )
}
