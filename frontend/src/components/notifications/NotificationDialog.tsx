import { Suspense } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'

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
import { useNotificationsQuery, notificationQueryKeys } from '@/hooks/useNotificationsQuery'
import { useDeleteNotificationMutation } from '@/hooks/useDeleteNotificationMutation'
import { ApiClient } from '@/lib/api'

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
  const queryClient = useQueryClient()

  const inboxData = useNotificationsQuery(0, 20)
  const deleteNotificationMutation = useDeleteNotificationMutation()

  const notifications = inboxData?.notifications ?? []
  const hasNotifications = notifications.length > 0

  const handleDelete = (id: string) => {
    deleteNotificationMutation.mutate(id)
  }

  const handleClearAll = async () => {
    try {
      // Delete all notifications (mark-all-read = delete-all in arca.live style)
      await ApiClient.delete('/api/notifications/all')
      void queryClient.invalidateQueries({ queryKey: notificationQueryKeys.all })
    } catch (error) {
      console.error('Failed to clear notifications:', error)
    }
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
        >
          {t('notifications.clearAll')}
        </Button>
      </DialogFooter>
    </>
  )
}

/**
 * Notification dialog component.
 * Displays user notifications in a scrollable dialog.
 * Clicking a notification navigates to the planner/comment and deletes it (arca.live style).
 */
export function NotificationDialog({
  open,
  onOpenChange,
}: NotificationDialogProps) {
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

        <Suspense fallback={<NotificationListSkeleton />}>
          <NotificationList onNavigate={handleNavigate} />
        </Suspense>
      </DialogContent>
    </Dialog>
  )
}
