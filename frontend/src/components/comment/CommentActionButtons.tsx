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

import { Reply, Edit, Trash2, ThumbsUp, Bell, BellOff, MoreHorizontal } from 'lucide-react'

import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

import type { CommentNode } from '@/types/CommentTypes'

interface CommentActionButtonsProps {
  comment: CommentNode
  isPublished: boolean
  isAuthenticated: boolean
  onReply: () => void
  onEdit: () => void
  onDelete: () => void
  onUpvote: () => void
  onToggleNotifications: () => void
  /** Report callback - temporarily disabled, kept for API stability */
  onReport?: () => void
  isUpvoting?: boolean
}

export function CommentActionButtons({
  comment,
  isPublished,
  isAuthenticated,
  onReply,
  onEdit,
  onDelete,
  onUpvote,
  onToggleNotifications,
  isUpvoting = false,
}: CommentActionButtonsProps) {
  // Don't show any actions if planner is unpublished
  if (!isPublished) return null

  const hasMenuItems = isAuthenticated || comment.isAuthor

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
        {/* Reply button (authenticated only) */}
        {isAuthenticated && (
          <Button variant="ghost" size="sm" className="h-7 px-2" onClick={onReply}>
            <Reply className="size-3.5" />
          </Button>
        )}

        {/* Author-only buttons */}
        {comment.isAuthor && (
          <>
            <Button variant="ghost" size="sm" className="h-7 px-2" onClick={onEdit}>
              <Edit className="size-3.5" />
            </Button>
            <Button variant="ghost" size="sm" className="h-7 px-2" onClick={onDelete}>
              <Trash2 className="size-3.5" />
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className="h-7 px-2"
              onClick={onToggleNotifications}
            >
              {comment.authorNotificationsEnabled ? (
                <Bell className="size-3.5 fill-current text-primary" />
              ) : (
                <BellOff className="size-3.5 text-muted-foreground" />
              )}
            </Button>
          </>
        )}
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
            {isAuthenticated && (
              <DropdownMenuItem onClick={onReply}>
                <Reply className="size-4 mr-2" />
                Reply
              </DropdownMenuItem>
            )}
            {comment.isAuthor && (
              <>
                <DropdownMenuItem onClick={onEdit}>
                  <Edit className="size-4 mr-2" />
                  Edit
                </DropdownMenuItem>
                <DropdownMenuItem onClick={onDelete} className="text-destructive">
                  <Trash2 className="size-4 mr-2" />
                  Delete
                </DropdownMenuItem>
                <DropdownMenuItem onClick={onToggleNotifications}>
                  {comment.authorNotificationsEnabled ? (
                    <>
                      <BellOff className="size-4 mr-2" />
                      Mute replies
                    </>
                  ) : (
                    <>
                      <Bell className="size-4 mr-2" />
                      Unmute replies
                    </>
                  )}
                </DropdownMenuItem>
              </>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </div>
  )
}
