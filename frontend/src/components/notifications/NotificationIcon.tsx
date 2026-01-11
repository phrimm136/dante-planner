import { Bell } from 'lucide-react'

import { Button } from '@/components/ui/button'

interface NotificationIconProps {
  /** Unread notification count */
  unreadCount: number
  /** Click handler for opening notification dialog */
  onClick: () => void
}

/**
 * Notification bell icon button with unread count badge.
 * Shows "99+" for counts >99, hides badge when count is 0.
 *
 * Pattern: Inline badge from PlannerCard.tsx (lines 86-88)
 * Icon: lucide-react Bell icon
 *
 * @example
 * ```tsx
 * <NotificationIcon
 *   unreadCount={5}
 *   onClick={() => setDialogOpen(true)}
 * />
 * ```
 */
export function NotificationIcon({ unreadCount, onClick }: NotificationIconProps) {
  const displayCount = unreadCount > 99 ? '99+' : unreadCount.toString()

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={onClick}
      className="relative"
      aria-label="Notifications"
    >
      <Bell className="size-5" />
      {unreadCount > 0 && (
        <span className="absolute top-1 right-1 flex size-4 items-center justify-center rounded-full bg-destructive text-[10px] font-semibold text-white">
          {displayCount}
        </span>
      )}
    </Button>
  )
}
