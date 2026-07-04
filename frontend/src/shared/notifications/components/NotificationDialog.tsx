import { Suspense } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { NotificationItem } from './NotificationItem'
import { useNotificationPermission } from '../hooks/useNotificationPermission'
import { useNotificationsQuery } from '../hooks/useNotificationsQuery'
import { useDeleteNotificationMutation } from '../hooks/useDeleteNotificationMutation'
import { useClearAllNotificationsMutation } from '../hooks/useClearAllNotificationsMutation'

interface NotificationDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

function NotificationListSkeleton() {
  return (
    <div className="space-y-2">
      {[1, 2, 3].map((i) => (
        <div key={i} className="flex items-start gap-3 p-3">
          <Skeleton className="size-5 rounded-full" />
          <div className="flex-1 space-y-1">
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-3 w-1/2" />
            <Skeleton className="h-3 w-1/4" />
          </div>
        </div>
      ))}
    </div>
  )
}

function NotificationList({
  onNavigate,
}: {
  onNavigate: (plannerId: string, commentPublicId: string | null) => void
}) {
  const { t } = useTranslation(['common'])

  const inboxData = useNotificationsQuery(0, 20)
  const deleteNotificationMutation = useDeleteNotificationMutation()
  const clearAllMutation = useClearAllNotificationsMutation()

  const notifications = inboxData?.notifications ?? []
  const hasNotifications = notifications.length > 0

  const handleDelete = (id: string) => {
    deleteNotificationMutation.mutate(id)
  }

  const handleClearAll = () => {
    clearAllMutation.mutate()
  }

  if (!hasNotifications) {
    return (
      <div className="text-sm text-muted-foreground text-center py-8">
        {t('notifications.empty')}
      </div>
    )
  }

  return (
    <>
      <div className="space-y-1 max-h-[400px] overflow-y-auto">
        {notifications.map((notification) => (
          <NotificationItem
            key={notification.id}
            notification={notification}
            onNavigate={onNavigate}
            onDelete={handleDelete}
          />
        ))}
      </div>

      <DialogFooter className="mt-4">
        <Button
          variant="outline"
          size="sm"
          onClick={handleClearAll}
          disabled={clearAllMutation.isPending}
        >
          {t('notifications.clearAll')}
        </Button>
      </DialogFooter>
    </>
  )
}

/**
 * Discovery CTA offering to enable desktop (OS) notifications.
 *
 * Gates only on the browser permission global (`default`) — never on the user's
 * server-side notification prefs, which live in the settings page and would
 * violate the sink rule if read here. Backed by the shared permission hook, so
 * granting here also updates the settings notice (and vice versa).
 */
function DesktopNotificationCta() {
  const { t } = useTranslation(['common'])
  const { state, request } = useNotificationPermission()

  if (state !== 'default') {
    return null
  }

  return (
    <div className="flex items-center justify-between gap-3 rounded-md border border-border bg-muted/50 px-3 py-2">
      <p className="text-sm text-muted-foreground">{t('notifications.desktopCta.message')}</p>
      <Button size="sm" variant="outline" className="shrink-0" onClick={request}>
        {t('notifications.desktopCta.enable')}
      </Button>
    </div>
  )
}

/**
 * Notification dialog component.
 * Displays user notifications in a scrollable dialog.
 * Clicking a notification navigates to the planner/comment and deletes it (arca.live style).
 */
export function NotificationDialog({ open, onOpenChange }: NotificationDialogProps) {
  const { t } = useTranslation(['common'])
  const navigate = useNavigate()

  const handleNavigate = (plannerId: string, commentPublicId: string | null) => {
    // Navigate to planner detail page with optional comment hash anchor
    const hash = commentPublicId ? `#comment-${commentPublicId}` : ''
    void navigate({
      to: '/planner/md/gesellschaft/$id',
      params: { id: plannerId },
      hash,
    })
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-full max-w-md">
        <DialogHeader>
          <DialogTitle>{t('notifications.title')}</DialogTitle>
        </DialogHeader>

        <DesktopNotificationCta />

        <Suspense fallback={<NotificationListSkeleton />}>
          <NotificationList onNavigate={handleNavigate} />
        </Suspense>
      </DialogContent>
    </Dialog>
  )
}
