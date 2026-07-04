import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'

import { toast } from '@/lib/toast'
import { authQueryKeys } from '@/shared/auth'
import { useLogoutEverywhere } from '@/shared/auth'
import { Button } from '@/components/ui/button'
import { LogoutEverywhereDialog } from './LogoutEverywhereDialog'

/**
 * Settings section for logging out of every device.
 *
 * Renders a title, description, and button that opens a confirmation dialog.
 * On confirm it calls the logout-all endpoint, shows a toast, clears the auth
 * cache to trigger logged-out state, and redirects to the home page. Mirrors
 * the {@link AccountDeleteSection} pattern.
 *
 * Rendered only inside the authenticated-only block of SettingsPage, so no
 * sign-in prompt is needed here.
 */
export function LogoutEverywhereSection() {
  const { t } = useTranslation()
  const logoutEverywhere = useLogoutEverywhere()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [dialogOpen, setDialogOpen] = useState(false)

  const handleLogoutEverywhere = () => {
    logoutEverywhere.mutate(undefined, {
      onSuccess: () => {
        toast.success(t('settings.logoutEverywhere.success'))
        setDialogOpen(false)
        queryClient.setQueryData(authQueryKeys.me, null)
        void navigate({ to: '/' })
      },
      onError: () => {
        toast.error(t('settings.logoutEverywhere.error'))
      },
    })
  }

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">{t('settings.logoutEverywhere.title')}</h2>
      <p className="text-sm text-muted-foreground">{t('settings.logoutEverywhere.description')}</p>
      <Button variant="destructive" onClick={() => setDialogOpen(true)}>
        {t('settings.logoutEverywhere.button')}
      </Button>

      <LogoutEverywhereDialog
        open={dialogOpen}
        onConfirm={handleLogoutEverywhere}
        onCancel={() => setDialogOpen(false)}
        isPending={logoutEverywhere.isPending}
      />
    </div>
  )
}
