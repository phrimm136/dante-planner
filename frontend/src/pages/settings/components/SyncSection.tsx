import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { showSuccess } from '@/lib/errorPresentation'

import { useAuthQuery } from '@/shared/auth'
import { useUserSettingsQuery, useUpdateUserSettingsMutation } from '@/shared/userSettings'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { SECTION_STYLES } from '@/lib/constants'

/**
 * Inner component that uses Suspense hooks.
 * Must be wrapped in Suspense boundary.
 */
function SyncSectionContent() {
  const { t } = useTranslation()
  const { data: user } = useAuthQuery()
  const { data: settings, isLoading } = useUserSettingsQuery()
  const updateSettings = useUpdateUserSettingsMutation()

  // Unauthenticated state - don't render
  if (!user) {
    return null
  }

  // Loading state
  if (isLoading || !settings) {
    return <SyncSectionSkeleton />
  }

  const handleSyncToggle = (checked: boolean) => {
    updateSettings.mutate(
      { syncEnabled: checked },
      {
        onSuccess: (settings) => {
          showSuccess(
            settings.syncEnabled
              ? 'common:settings.sync.enabledSuccess'
              : 'common:settings.sync.disabledSuccess',
          )
        },
      },
    )
  }

  const syncEnabled = settings.syncEnabled

  return (
    <div className="space-y-4">
      <h2 className={SECTION_STYLES.TEXT.sectionTitle}>{t('settings.sync.title', 'Sync')}</h2>

      <div className={SECTION_STYLES.LAYOUT.rowBetween}>
        <div className="space-y-1">
          <Label htmlFor="sync-toggle" className="text-base">
            {t('settings.sync.label', 'Enable Sync')}
          </Label>
          <p className={SECTION_STYLES.TEXT.caption}>
            {syncEnabled
              ? t('settings.sync.descriptionOn', 'Your planners sync across devices')
              : t('settings.sync.descriptionOff', 'Your planners are stored locally only')}
          </p>
        </div>
        <Switch
          id="sync-toggle"
          checked={syncEnabled}
          onCheckedChange={handleSyncToggle}
          disabled={updateSettings.isPending}
        />
      </div>
    </div>
  )
}

/**
 * Sync section with Suspense boundary.
 * Public component for use in SettingsPage.
 */
export function SyncSection() {
  return (
    <Suspense fallback={<SyncSectionSkeleton />}>
      <SyncSectionContent />
    </Suspense>
  )
}

/**
 * Loading skeleton for sync section.
 */
function SyncSectionSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-6 w-16" />
      <div className={SECTION_STYLES.LAYOUT.rowBetween}>
        <div className="space-y-1">
          <Skeleton className="h-5 w-24" />
          <Skeleton className="h-4 w-48" />
        </div>
        <Skeleton className="h-5 w-10 rounded-full" />
      </div>
    </div>
  )
}
