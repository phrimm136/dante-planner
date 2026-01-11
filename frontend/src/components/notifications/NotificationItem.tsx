import { Trophy, MessageSquare, Reply, Flag } from 'lucide-react'
import { formatRelativeTime } from '@/lib/formatDate'
import { cn } from '@/lib/utils'
import type { NotificationResponse } from '@/types/NotificationTypes'

interface NotificationItemProps {
  notification: NotificationResponse
  onNavigate: (contentId: string) => void
  onRead: (id: number) => void
  onDelete: (id: number) => void
}

export function NotificationItem({
  notification,
  onNavigate,
  onRead,
  onDelete,
}: NotificationItemProps) {
  const { id, notificationType, contentId, read, createdAt } = notification

  const getIcon = () => {
    switch (notificationType) {
      case 'PLANNER_RECOMMENDED':
        return <Trophy className="h-5 w-5 text-yellow-500" />
      case 'COMMENT_RECEIVED':
        return <MessageSquare className="h-5 w-5 text-blue-500" />
      case 'REPLY_RECEIVED':
        return <Reply className="h-5 w-5 text-green-500" />
      case 'REPORT_RECEIVED':
        return <Flag className="h-5 w-5 text-red-500" />
      default:
        return <MessageSquare className="h-5 w-5 text-muted-foreground" />
    }
  }

  const getTitle = () => {
    switch (notificationType) {
      case 'PLANNER_RECOMMENDED':
        return 'Your planner is now recommended!'
      case 'COMMENT_RECEIVED':
        return 'New comment on your planner'
      case 'REPLY_RECEIVED':
        return 'Someone replied to your comment'
      case 'REPORT_RECEIVED':
        return 'Your content was reported'
      default:
        return 'New notification'
    }
  }

  const handleClick = () => {
    onNavigate(contentId)
    if (!read) {
      onRead(id)
    }
  }

  return (
    <div
      className={cn(
        'flex items-start gap-3 p-3 rounded-lg cursor-pointer transition-colors hover:bg-accent',
        !read && 'bg-accent/50'
      )}
      onClick={handleClick}
    >
      <div className="flex-shrink-0 mt-1">{getIcon()}</div>
      <div className="flex-1 min-w-0">
        <p className={cn('text-sm', !read && 'font-semibold')}>{getTitle()}</p>
        <p className="text-xs text-muted-foreground mt-1">
          {formatRelativeTime(createdAt)}
        </p>
      </div>
      {!read && (
        <div className="flex-shrink-0 mt-2">
          <div className="h-2 w-2 rounded-full bg-blue-500" />
        </div>
      )}
      <button
        className="flex-shrink-0 text-muted-foreground hover:text-foreground transition-colors"
        onClick={(e) => {
          e.stopPropagation()
          onDelete(id)
        }}
        aria-label="Dismiss notification"
      >
        <span className="text-xs">×</span>
      </button>
    </div>
  )
}
