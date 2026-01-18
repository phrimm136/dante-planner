import { Bell } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface NotificationIconProps {
  unreadCount: number
  onClick: () => void
}

/**
 * Notification bell icon with unread count badge
 */
export function NotificationIcon({ unreadCount, onClick }: NotificationIconProps) {
  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={onClick}
      className="relative"
      aria-label={`Notifications${unreadCount > 0 ? ` (${unreadCount} unread)` : ''}`}
    >
      <Bell className="size-5" />
      {unreadCount > 0 && (
        <span
          className={cn(
            'absolute -top-1 -right-1 flex items-center justify-center',
            'min-w-5 h-5 px-1 rounded-full',
            'bg-destructive text-destructive-foreground text-xs font-medium'
          )}
        >
          {unreadCount > 99 ? '99+' : unreadCount}
        </span>
      )}
    </Button>
  )
}
