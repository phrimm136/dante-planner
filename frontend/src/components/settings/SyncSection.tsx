import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { useAuthQuery } from '@/hooks/useAuthQuery'
import { useUserSettingsQuery, useUpdateUserSettingsMutation } from '@/hooks/useUserSettings'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'

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

  // Handle toggle - null means first-login (will redirect to dialog in Phase 6)
  const handleSyncToggle = (checked: boolean) => {
    updateSettings.mutate(
      { syncEnabled: checked },
      {
        onSuccess: () => {
          const message = checked
            ? t('settings.sync.enabledSuccess', 'Sync enabled')
            : t('settings.sync.disabledSuccess', 'Sync disabled')
          toast.success(message)
        },
        onError: () => {
          toast.error(t('settings.sync.updateError', 'Failed to update sync setting'))
        },
      }
    )
  }

  // syncEnabled = null means not chosen yet (first-login case)
  // For now, treat as OFF until Phase 6 implements the dialog
  const syncEnabled = settings.syncEnabled ?? false

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">{t('settings.sync.title', 'Sync')}</h2>

      <div className="flex items-center justify-between">
        <div className="space-y-1">
          <Label htmlFor="sync-toggle" className="text-base">
            {t('settings.sync.label', 'Enable Sync')}
          </Label>
          <p className="text-sm text-muted-foreground">
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
      <div className="flex items-center justify-between">
        <div className="space-y-1">
          <Skeleton className="h-5 w-24" />
          <Skeleton className="h-4 w-48" />
        </div>
        <Skeleton className="h-5 w-10 rounded-full" />
      </div>
    </div>
  )
}
