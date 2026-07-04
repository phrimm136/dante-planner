import { useState, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from '@/lib/toast'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'

import { useAuthQuery, authQueryKeys } from '@/shared/auth'
import { useDeleteAccountMutation } from '../hooks/useUserSettingsQuery'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { AccountDeleteDialog } from './AccountDeleteDialog'
import { startGoogleLogin } from '@/shared/auth'
import { GoogleIcon } from '@/components/ui/GoogleIcon'

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
          }),
        )

        // Close dialog
        setDialogOpen(false)

        // CRITICAL: Invalidate auth cache IMMEDIATELY to trigger logout
        queryClient.setQueryData(authQueryKeys.me, null)

        // Wait 2 seconds then redirect
        setTimeout(() => {
          void navigate({ to: '/' })
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
        <p className="text-muted-foreground">{t('settings.deleteAccount.signInPrompt')}</p>
        <Button onClick={startGoogleLogin} className="flex items-center gap-2">
          <GoogleIcon className="h-4 w-4" />
          {t('header.auth.googleLogin')}
        </Button>
      </div>
    )
  }

  // Authenticated state - show delete button
  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">{t('settings.deleteAccount.title')}</h2>
      <p className="text-sm text-muted-foreground">{t('settings.deleteAccount.warning')}</p>
      <Button variant="destructive" onClick={() => setDialogOpen(true)}>
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
