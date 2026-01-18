/**
 * In-app Notification Toast
 *
 * Shows notification popup in bottom-right when tab is focused.
 * Clicking navigates to the relevant content.
 * Used instead of browser notification when tab is visible.
 */

import { toast } from 'sonner'
import { Bell, FileText, MessageSquare, Star } from 'lucide-react'

import { cn } from '@/lib/utils'

import type { ReactNode } from 'react'

interface NotificationToastData {
  /** Notification type for icon selection */
  type: 'COMMENT_RECEIVED' | 'REPLY_RECEIVED' | 'PLANNER_RECOMMENDED' | 'PLANNER_PUBLISHED'
  /** Toast title */
  title: string
  /** Toast body text */
  body: string
  /** URL to navigate on click */
  url?: string
}

/**
 * Get icon for notification type
 */
function getNotificationIcon(type: NotificationToastData['type']): ReactNode {
  switch (type) {
    case 'COMMENT_RECEIVED':
    case 'REPLY_RECEIVED':
      return <MessageSquare className="size-5 text-primary" />
    case 'PLANNER_RECOMMENDED':
      return <Star className="size-5 text-yellow-500" />
    case 'PLANNER_PUBLISHED':
      return <FileText className="size-5 text-purple-500" />
    default:
      return <Bell className="size-5 text-primary" />
  }
}

/**
 * Notification toast content component
 */
function NotificationToastContent({
  data,
  toastId,
}: {
  data: NotificationToastData
  toastId: string | number
}) {
  const handleClick = () => {
    toast.dismiss(toastId)
    if (data.url) {
      // Use window.location for navigation to handle hash fragments properly
      window.location.href = data.url
    }
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      className={cn(
        'flex items-start gap-3 w-full text-left',
        'bg-background border border-border rounded-lg p-4 shadow-lg',
        'hover:bg-accent transition-colors cursor-pointer'
      )}
    >
      <div className="flex-shrink-0 mt-0.5">
        {getNotificationIcon(data.type)}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-foreground">{data.title}</p>
        {data.body && (
          <p className="text-sm text-muted-foreground line-clamp-2 mt-0.5">
            {data.body}
          </p>
        )}
      </div>
    </button>
  )
}

/**
 * Show in-app notification toast in bottom-right corner.
 * Use this when tab is visible instead of browser notification.
 */
export function showNotificationToast(data: NotificationToastData): void {
  toast.custom(
    (id) => <NotificationToastContent data={data} toastId={id} />,
    {
      duration: 5000,
      position: 'bottom-right',
      unstyled: true,
    }
  )
}

export type { NotificationToastData }
