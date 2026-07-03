import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from '@/lib/toast'

import { useAuthQuery } from '@/shared/auth'
import { useUserSettingsQuery, useUpdateUserSettingsMutation } from '../hooks/useUserSettings'
import { useNotificationPermission } from '@/shared/notifications'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * Inner component that uses Suspense hooks.
 * Must be wrapped in Suspense boundary.
 */
function NotificationSectionContent() {
  const { t } = useTranslation()
  const { data: user } = useAuthQuery()
  const { data: settings, isLoading } = useUserSettingsQuery()
  const updateSettings = useUpdateUserSettingsMutation()
  const { state: permissionState, request: requestPermission } = useNotificationPermission()

  // Unauthenticated state - don't render
  if (!user) {
    return null
  }

  // Loading state
  if (isLoading || !settings) {
    return <NotificationSectionSkeleton />
  }

  const anyNotificationEnabled =
    settings.notifyComments || settings.notifyRecommendations || settings.notifyNewPublications
  // A pref is on but the browser hasn't granted OS delivery — desktop notifications
  // silently won't fire. 'default' is fixable here with a click; 'denied' can only
  // be re-allowed in the browser's own site settings.
  const showPermissionNotice =
    anyNotificationEnabled && (permissionState === 'default' || permissionState === 'denied')

  // Generic handler for notification toggles
  const handleToggle = (
    key: 'notifyComments' | 'notifyRecommendations' | 'notifyNewPublications',
    checked: boolean
  ) => {
    // Request browser notification permission when enabling any notification
    if (checked) {
      requestPermission()
    }

    updateSettings.mutate(
      { [key]: checked },
      {
        onSuccess: () => {
          toast.success(t('settings.notifications.updateSuccess', 'Notification setting updated'))
        },
        onError: () => {
          toast.error(t('settings.notifications.updateError', 'Failed to update notification setting'))
        },
      }
    )
  }

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">{t('settings.notifications.title', 'Notifications')}</h2>

      {showPermissionNotice && (
        <div className="flex items-center justify-between gap-3 rounded-md border border-border bg-muted/50 px-3 py-2">
          <p className="text-sm text-muted-foreground">
            {permissionState === 'denied'
              ? t(
                  'settings.notifications.permission.deniedMessage',
                  "Your browser has blocked notifications for this site. Re-allow them in your browser's site settings to receive desktop alerts."
                )
              : t(
                  'settings.notifications.permission.defaultMessage',
                  'Browser notifications are not enabled. Alerts show only in-app while a tab is open.'
                )}
          </p>
          {permissionState === 'default' && (
            <Button
              variant="outline"
              size="sm"
              className="shrink-0"
              onClick={requestPermission}
            >
              {t('settings.notifications.permission.enable', 'Enable browser notifications')}
            </Button>
          )}
        </div>
      )}

      <div className="space-y-4">
        {/* Comments notification */}
        <div className="flex items-center justify-between">
          <div className="space-y-1">
            <Label htmlFor="notify-comments" className="text-base">
              {t('settings.notifications.comments.label', 'Comments')}
            </Label>
            <p className="text-sm text-muted-foreground">
              {t('settings.notifications.comments.description', 'Notify when someone comments on your planner')}
            </p>
          </div>
          <Switch
            id="notify-comments"
            checked={settings.notifyComments}
            onCheckedChange={(checked) => handleToggle('notifyComments', checked)}
            disabled={updateSettings.isPending}
          />
        </div>

        {/* Recommendations notification */}
        <div className="flex items-center justify-between">
          <div className="space-y-1">
            <Label htmlFor="notify-recommendations" className="text-base">
              {t('settings.notifications.recommendations.label', 'Recommendations')}
            </Label>
            <p className="text-sm text-muted-foreground">
              {t('settings.notifications.recommendations.description', 'Notify when your planner reaches recommended status')}
            </p>
          </div>
          <Switch
            id="notify-recommendations"
            checked={settings.notifyRecommendations}
            onCheckedChange={(checked) => handleToggle('notifyRecommendations', checked)}
            disabled={updateSettings.isPending}
          />
        </div>

        {/* New publications notification */}
        <div className="flex items-center justify-between">
          <div className="space-y-1">
            <Label htmlFor="notify-new-publications" className="text-base">
              {t('settings.notifications.newPublications.label', 'New Publications')}
            </Label>
            <p className="text-sm text-muted-foreground">
              {t('settings.notifications.newPublications.description', 'Notify when someone publishes a new planner')}
            </p>
          </div>
          <Switch
            id="notify-new-publications"
            checked={settings.notifyNewPublications}
            onCheckedChange={(checked) => handleToggle('notifyNewPublications', checked)}
            disabled={updateSettings.isPending}
          />
        </div>
      </div>
    </div>
  )
}

/**
 * Notification section with Suspense boundary.
 * Public component for use in SettingsPage.
 */
export function NotificationSection() {
  return (
    <Suspense fallback={<NotificationSectionSkeleton />}>
      <NotificationSectionContent />
    </Suspense>
  )
}

/**
 * Loading skeleton for notification section.
 */
function NotificationSectionSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-6 w-28" />
      <div className="space-y-4">
        {[1, 2, 3].map((i) => (
          <div key={i} className="flex items-center justify-between">
            <div className="space-y-1">
              <Skeleton className="h-5 w-32" />
              <Skeleton className="h-4 w-64" />
            </div>
            <Skeleton className="h-5 w-10 rounded-full" />
          </div>
        ))}
      </div>
    </div>
  )
}
