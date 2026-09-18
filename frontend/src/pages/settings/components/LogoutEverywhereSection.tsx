import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'

import { showSuccess } from '@/lib/errorPresentation'
import { authQueryKeys } from '@/shared/auth'
import { useLogoutEverywhere } from '@/shared/auth'
import { Button } from '@/components/ui/button'
import { LogoutEverywhereDialog } from './LogoutEverywhereDialog'
import { SECTION_STYLES } from '@/lib/constants'

export function LogoutEverywhereSection() {
  const { t } = useTranslation()
  const logoutEverywhere = useLogoutEverywhere()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [dialogOpen, setDialogOpen] = useState(false)

  const handleLogoutEverywhere = () => {
    logoutEverywhere.mutate(undefined, {
      onSuccess: () => {
        showSuccess('common:settings.logoutEverywhere.success')
        setDialogOpen(false)
        queryClient.setQueryData(authQueryKeys.me, null)
        void navigate({ to: '/' })
      },
    })
  }

  return (
    <div className="space-y-4">
      <h2 className={SECTION_STYLES.TEXT.sectionTitle}>{t('settings.logoutEverywhere.title')}</h2>
      <p className={SECTION_STYLES.TEXT.caption}>{t('settings.logoutEverywhere.description')}</p>
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
