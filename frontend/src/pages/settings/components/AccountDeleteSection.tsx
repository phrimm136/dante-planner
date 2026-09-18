import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { SECTION_STYLES } from '@/lib/constants'
import { useAuthQuery } from '@/shared/auth'
import { useDeleteAccountFlow } from '../hooks/useDeleteAccountFlow'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { AccountDeleteDialog } from './AccountDeleteDialog'
import { startGoogleLogin } from '@/shared/auth'
import { GoogleIcon } from '@/components/ui/GoogleIcon'

function AccountDeleteSectionContent() {
  const { t } = useTranslation()
  const { data: user } = useAuthQuery()
  const { dialogOpen, openDialog, closeDialog, confirmDelete, isPending } = useDeleteAccountFlow()

  if (!user) {
    return (
      <div className="space-y-4">
        <h2 className={SECTION_STYLES.TEXT.sectionTitle}>{t('settings.deleteAccount.title')}</h2>
        <p className={SECTION_STYLES.TEXT.muted}>{t('settings.deleteAccount.signInPrompt')}</p>
        <Button onClick={startGoogleLogin} className={SECTION_STYLES.LAYOUT.row}>
          <GoogleIcon className="h-4 w-4" />
          {t('header.auth.googleLogin')}
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <h2 className={SECTION_STYLES.TEXT.sectionTitle}>{t('settings.deleteAccount.title')}</h2>
      <p className={SECTION_STYLES.TEXT.caption}>{t('settings.deleteAccount.warning')}</p>
      <Button variant="destructive" onClick={openDialog}>
        {t('settings.deleteAccount.title')}
      </Button>

      <AccountDeleteDialog
        open={dialogOpen}
        onConfirm={confirmDelete}
        onCancel={closeDialog}
        isPending={isPending}
      />
    </div>
  )
}

export function AccountDeleteSection() {
  return (
    <Suspense fallback={<AccountDeleteSectionSkeleton />}>
      <AccountDeleteSectionContent />
    </Suspense>
  )
}

function AccountDeleteSectionSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-6 w-32" />
      <Skeleton className="h-4 w-64" />
      <Skeleton className="h-10 w-32" />
    </div>
  )
}
