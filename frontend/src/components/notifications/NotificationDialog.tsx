import { useNavigate } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { ScrollArea } from '@/components/ui/scroll-area'
import { NotificationItem } from './NotificationItem'
import { useNotificationsQuery } from '@/hooks/useNotificationsQuery'
import { useMarkReadMutation } from '@/hooks/useMarkReadMutation'
import { useDeleteNotificationMutation } from '@/hooks/useDeleteNotificationMutation'

interface NotificationDialogProps {
  /** Whether dialog is open */
  open: boolean
  /** Callback when open state changes */
  onOpenChange: (open: boolean) => void
}

/**
 * Notification dialog component.
 * Displays user notifications in a scrollable dialog.
 * Positioned below notification bell icon, right-aligned.
 */
export function NotificationDialog({
  open,
  onOpenChange,
}: NotificationDialogProps) {
  const navigate = useNavigate()

  // Fetch notifications (first page, 20 items)
  const { data: inboxData, isLoading } = useNotificationsQuery(0, 20)
  const markReadMutation = useMarkReadMutation()
  const deleteNotificationMutation = useDeleteNotificationMutation()

  const notifications = inboxData?.notifications ?? []
  const hasNotifications = notifications.length > 0

  const handleNavigate = (contentId: string) => {
    // Navigate to the related content (planner or comment)
    // Close dialog after navigation
    navigate({ to: `/planner/md/${contentId}` })
    onOpenChange(false)
  }

  const handleMarkRead = (id: number) => {
    markReadMutation.mutate(id)
  }

  const handleDelete = (id: number) => {
    deleteNotificationMutation.mutate(id)
  }

  const handleMarkAllRead = () => {
    // Mark all unread notifications as read
    const unreadIds = notifications
      .filter((n) => !n.read)
      .map((n) => n.id)

    unreadIds.forEach((id) => markReadMutation.mutate(id))
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-full max-w-[400px] sm:max-w-[90vw]">
        <DialogHeader>
          <DialogTitle>Notifications</DialogTitle>
        </DialogHeader>

        {/* Notification list */}
        <ScrollArea className="max-h-[600px] pr-4">
          {isLoading ? (
            <div className="text-sm text-muted-foreground text-center py-8">
              Loading notifications...
            </div>
          ) : !hasNotifications ? (
            <div className="text-sm text-muted-foreground text-center py-8">
              No notifications
            </div>
          ) : (
            <div className="space-y-2">
              {notifications.map((notification) => (
                <NotificationItem
                  key={notification.id}
                  notification={notification}
                  onNavigate={handleNavigate}
                  onRead={handleMarkRead}
                  onDelete={handleDelete}
                />
              ))}
            </div>
          )}
        </ScrollArea>

        {/* Footer with "Mark all as read" button */}
        {hasNotifications && (
          <DialogFooter>
            <Button
              variant="outline"
              onClick={handleMarkAllRead}
              disabled={!notifications.some((n) => !n.read)}
            >
              Mark all as read
            </Button>
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  )
}
