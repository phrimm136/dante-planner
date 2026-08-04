/**
 * CommentActionButtons
 *
 * Action buttons row for comments (arca.live style).
 * Shows different buttons based on:
 * - isPublished: All buttons hidden if planner is unpublished
 * - isAuthor: Edit, delete, notification toggle only for comment author
 * - isAuthenticated: Reply, vote require login (checked via isAuthor field presence)
 *
 * Responsive:
 * - Wide screens (sm+): Inline buttons
 * - Narrow screens: Dropdown menu with hamburger
 */

import type { ReactNode } from 'react'
import { Reply, Edit, Trash2, ThumbsUp, Bell, BellOff, MoreHorizontal } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

import type { CommentNode } from '../types/CommentTypes'

interface CommentActionButtonsProps {
  comment: CommentNode
  isPublished: boolean
  isAuthenticated: boolean
  isModerator: boolean
  onReply: () => void
  onEdit: () => void
  onDelete: () => void
  onModeratorDelete: () => void
  onUpvote: () => void
  onToggleNotifications: () => void
  /** Report callback - temporarily disabled, kept for API stability */
  onReport?: () => void
  isUpvoting?: boolean
}

/** One action rendered by both the inline row and the dropdown menu. */
interface CommentAction {
  key: string
  onSelect: () => void
  inlineIcon: ReactNode
  inlineClassName?: string
  menuIcon: ReactNode
  menuLabel: ReactNode
  menuClassName?: string
}

export function CommentActionButtons({
  comment,
  isPublished,
  isAuthenticated,
  isModerator,
  onReply,
  onEdit,
  onDelete,
  onModeratorDelete,
  onUpvote,
  onToggleNotifications,
  isUpvoting = false,
}: CommentActionButtonsProps) {
  const { t } = useTranslation()
  // Don't show any actions if planner is unpublished
  if (!isPublished) return null

  const hasMenuItems = isAuthenticated || comment.isAuthor || isModerator

  const actions: CommentAction[] = []

  if (isAuthenticated) {
    actions.push({
      key: 'reply',
      onSelect: onReply,
      inlineIcon: <Reply className="size-3.5" />,
      menuIcon: <Reply className="size-4 mr-2" />,
      menuLabel: 'Reply',
    })
  }

  if (comment.isAuthor) {
    actions.push(
      {
        key: 'edit',
        onSelect: onEdit,
        inlineIcon: <Edit className="size-3.5" />,
        menuIcon: <Edit className="size-4 mr-2" />,
        menuLabel: 'Edit',
      },
      {
        key: 'delete',
        onSelect: onDelete,
        inlineIcon: <Trash2 className="size-3.5" />,
        menuIcon: <Trash2 className="size-4 mr-2" />,
        menuLabel: 'Delete',
        menuClassName: 'text-destructive',
      },
      {
        key: 'notifications',
        onSelect: onToggleNotifications,
        inlineIcon: comment.authorNotificationsEnabled ? (
          <Bell className="size-3.5 fill-current text-primary" />
        ) : (
          <BellOff className="size-3.5 text-muted-foreground" />
        ),
        menuIcon: comment.authorNotificationsEnabled ? (
          <BellOff className="size-4 mr-2" />
        ) : (
          <Bell className="size-4 mr-2" />
        ),
        menuLabel: comment.authorNotificationsEnabled
          ? t('comments.muteReplies')
          : t('comments.unmuteReplies'),
      },
    )
  }

  if (isModerator && !comment.isAuthor) {
    actions.push({
      key: 'moderatorDelete',
      onSelect: onModeratorDelete,
      inlineIcon: <Trash2 className="size-3.5" />,
      inlineClassName: 'text-orange-500 hover:text-orange-600',
      menuIcon: <Trash2 className="size-4 mr-2" />,
      menuLabel: t('common:moderation.deleteComment', 'Delete (Mod)'),
      menuClassName: 'text-orange-500',
    })
  }

  return (
    <div className="flex items-center gap-1 text-muted-foreground">
      {/* Upvote button + count (always visible) */}
      <Button
        variant="ghost"
        size="sm"
        className={cn('h-7 px-2 gap-1', comment.hasUpvoted && 'text-primary')}
        onClick={onUpvote}
        disabled={comment.hasUpvoted || isUpvoting}
      >
        <ThumbsUp className="size-3.5" />
        <span className="text-xs">{comment.upvoteCount}</span>
      </Button>

      {/* Desktop: Inline buttons (hidden on mobile) */}
      <div className="hidden sm:flex items-center gap-1">
        {actions.map((action) => (
          <Button
            key={action.key}
            variant="ghost"
            size="sm"
            className={cn('h-7 px-2', action.inlineClassName)}
            onClick={action.onSelect}
          >
            {action.inlineIcon}
          </Button>
        ))}
      </div>

      {/* Mobile: Dropdown menu (visible only on mobile) */}
      {hasMenuItems && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="sm" className="h-7 px-2 sm:hidden">
              <MoreHorizontal className="size-3.5" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {actions.map((action) => (
              <DropdownMenuItem
                key={action.key}
                onClick={action.onSelect}
                className={action.menuClassName}
              >
                {action.menuIcon}
                {action.menuLabel}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </div>
  )
}
