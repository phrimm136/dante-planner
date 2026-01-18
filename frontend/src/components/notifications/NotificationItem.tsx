import { useTranslation } from 'react-i18next'
import { Star, FileText, MessageSquare, Reply, AlertTriangle, X } from 'lucide-react'

import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { formatRelativeTime } from '@/lib/formatDate'

import type { NotificationResponse, NotificationType } from '@/types/NotificationTypes'

interface NotificationItemProps {
  notification: NotificationResponse
  onNavigate: (plannerId: string, commentPublicId: string | null) => void
  onDelete: (id: string) => void
}

const NOTIFICATION_CONFIG: Record<NotificationType, { icon: typeof Star; color: string; labelKey: string }> = {
  PLANNER_RECOMMENDED: {
    icon: Star,
    color: 'text-yellow-500',
    labelKey: 'notifications.types.plannerRecommended',
  },
  PLANNER_PUBLISHED: {
    icon: FileText,
    color: 'text-purple-500',
    labelKey: 'notifications.types.plannerPublished',
  },
  COMMENT_RECEIVED: {
    icon: MessageSquare,
    color: 'text-blue-500',
    labelKey: 'notifications.types.commentReceived',
  },
  REPLY_RECEIVED: {
    icon: Reply,
    color: 'text-green-500',
    labelKey: 'notifications.types.replyReceived',
  },
  REPORT_RECEIVED: {
    icon: AlertTriangle,
    color: 'text-orange-500',
    labelKey: 'notifications.types.reportReceived',
  },
}

/**
 * Single notification row with icon, content preview, and delete action.
 * Clicking the row navigates and deletes (arca.live style).
 */
export function NotificationItem({
  notification,
  onNavigate,
  onDelete,
}: NotificationItemProps) {
  const { t } = useTranslation(['common'])
  const config = NOTIFICATION_CONFIG[notification.notificationType]
  const Icon = config.icon

  const formattedTime = formatRelativeTime(notification.createdAt)

  // For PLANNER_RECOMMENDED, contentId is the plannerId
  // For COMMENT/REPLY, use plannerId field
  const plannerId = notification.plannerId ?? notification.contentId

  const handleClick = () => {
    // Delete on click (mark as read = delete, arca.live style)
    onDelete(notification.id)
    onNavigate(plannerId, notification.commentPublicId)
  }

  const handleDelete = (e: React.MouseEvent) => {
    e.stopPropagation()
    onDelete(notification.id)
  }

  return (
    <div
      className={cn(
        'flex items-start gap-3 p-3 rounded-lg cursor-pointer transition-colors',
        'hover:bg-accent',
        !notification.read && 'bg-accent/50'
      )}
      onClick={handleClick}
    >
      {/* Icon */}
      <div className={cn('shrink-0 mt-0.5', config.color)}>
        <Icon className="size-5" />
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <p className={cn('text-sm', !notification.read && 'font-medium')}>
          {t(config.labelKey)}
        </p>
        {/* Plan title */}
        {notification.plannerTitle && (
          <p className="text-sm text-foreground/80 truncate mt-0.5">
            {notification.plannerTitle}
          </p>
        )}
        {/* Comment snippet */}
        {notification.commentSnippet && (
          <p className="text-xs text-muted-foreground truncate mt-0.5">
            {notification.commentSnippet}
          </p>
        )}
        <p className="text-xs text-muted-foreground mt-1">
          {formattedTime}
        </p>
      </div>

      {/* Delete button */}
      <Button
        variant="ghost"
        size="icon-sm"
        onClick={handleDelete}
        aria-label={t('notifications.delete')}
        className="shrink-0 text-muted-foreground hover:text-destructive"
      >
        <X className="size-4" />
      </Button>
    </div>
  )
}
