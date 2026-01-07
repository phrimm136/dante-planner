import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import {
  Eye,
  Edit,
  Copy,
  Globe,
  Trash2,
  GitFork,
  Bookmark,
  ThumbsUp,
  ThumbsDown,
} from 'lucide-react'

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

import { usePlannerVote } from '@/hooks/usePlannerVote'
import { usePlannerBookmark } from '@/hooks/usePlannerBookmark'
import { usePlannerFork } from '@/hooks/usePlannerFork'

import type { PublicPlanner, PlannerListView } from '@/types/PlannerListTypes'

interface PlannerCardContextMenuProps {
  /** Planner data for context menu actions */
  planner: PublicPlanner
  /** Current view mode */
  view: PlannerListView
  /** Whether user is authenticated */
  isAuthenticated: boolean
  /** Child element (PlannerCard) */
  children: React.ReactNode
  /** Optional callback when publish/unpublish is clicked */
  onPublishToggle?: (plannerId: string) => void
  /** Optional callback when delete is clicked */
  onDelete?: (plannerId: string) => void
  /** Optional callback when duplicate is clicked */
  onDuplicate?: (plannerId: string) => void
}

/**
 * Context menu wrapper for PlannerCard.
 * Shows different actions based on view mode and auth state.
 *
 * My Plans view:
 * - Edit, Duplicate, Publish/Unpublish, Delete
 *
 * Community view (authenticated):
 * - View, Fork, Bookmark, Upvote, Downvote
 *
 * Community view (guest):
 * - View only
 *
 * @example
 * <PlannerCardContextMenu
 *   planner={planner}
 *   view="community"
 *   isAuthenticated={true}
 * >
 *   <PlannerCard planner={planner} />
 * </PlannerCardContextMenu>
 */
export function PlannerCardContextMenu({
  planner,
  view,
  isAuthenticated,
  children,
  onPublishToggle,
  onDelete,
  onDuplicate,
}: PlannerCardContextMenuProps) {
  const { t } = useTranslation(['planner', 'common'])
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()

  const voteMutation = usePlannerVote()
  const bookmarkMutation = usePlannerBookmark()
  const forkMutation = usePlannerFork()

  const handleView = () => {
    void navigate({
      to: '/planner/md/$id',
      params: { id: planner.id },
    })
    setOpen(false)
  }

  const handleEdit = () => {
    void navigate({
      to: '/planner/md/$id/edit',
      params: { id: planner.id },
    })
    setOpen(false)
  }

  const handleDuplicate = () => {
    onDuplicate?.(planner.id)
    setOpen(false)
  }

  const handlePublishToggle = () => {
    onPublishToggle?.(planner.id)
    setOpen(false)
  }

  const handleDelete = () => {
    onDelete?.(planner.id)
    setOpen(false)
  }

  const handleFork = () => {
    forkMutation.mutate(planner.id, {
      onSuccess: (result) => {
        void navigate({
          to: '/planner/md/$id/edit',
          params: { id: result.newPlannerId },
        })
      },
    })
    setOpen(false)
  }

  const handleBookmark = () => {
    bookmarkMutation.mutate(planner.id)
    setOpen(false)
  }

  const handleUpvote = () => {
    const newVote = planner.userVote === 'UP' ? null : 'UP'
    voteMutation.mutate({ plannerId: planner.id, voteType: newVote })
    setOpen(false)
  }

  const handleDownvote = () => {
    const newVote = planner.userVote === 'DOWN' ? null : 'DOWN'
    voteMutation.mutate({ plannerId: planner.id, voteType: newVote })
    setOpen(false)
  }

  const isPending =
    voteMutation.isPending || bookmarkMutation.isPending || forkMutation.isPending

  // Render My Plans actions
  if (view === 'my-plans') {
    return (
      <DropdownMenu open={open} onOpenChange={setOpen}>
        <DropdownMenuTrigger asChild>
          <div
            onContextMenu={(e) => {
              e.preventDefault()
              setOpen(true)
            }}
          >
            {children}
          </div>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start">
          <DropdownMenuItem onClick={handleEdit}>
            <Edit className="size-4" />
            {t('pages.plannerList.contextMenu.edit')}
          </DropdownMenuItem>
          <DropdownMenuItem onClick={handleDuplicate}>
            <Copy className="size-4" />
            {t('pages.plannerList.contextMenu.duplicate')}
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={handlePublishToggle}>
            <Globe className="size-4" />
            {t('pages.plannerList.contextMenu.publish')}
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            onClick={handleDelete}
            variant="destructive"
          >
            <Trash2 className="size-4" />
            {t('pages.plannerList.contextMenu.delete')}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    )
  }

  // Render Community actions
  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <div
          onContextMenu={(e) => {
            e.preventDefault()
            setOpen(true)
          }}
        >
          {children}
        </div>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        <DropdownMenuItem onClick={handleView}>
          <Eye className="size-4" />
          {t('pages.plannerList.contextMenu.view')}
        </DropdownMenuItem>

        {isAuthenticated && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={handleFork} disabled={isPending}>
              <GitFork className="size-4" />
              {t('pages.plannerList.contextMenu.fork')}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={handleBookmark} disabled={isPending}>
              <Bookmark
                className={
                  planner.isBookmarked
                    ? 'size-4 fill-current'
                    : 'size-4'
                }
              />
              {planner.isBookmarked
                ? t('pages.plannerList.contextMenu.removeBookmark')
                : t('pages.plannerList.contextMenu.bookmark')}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={handleUpvote} disabled={isPending}>
              <ThumbsUp
                className={
                  planner.userVote === 'UP'
                    ? 'size-4 fill-current text-primary'
                    : 'size-4'
                }
              />
              {planner.userVote === 'UP'
                ? t('pages.plannerList.contextMenu.removeUpvote')
                : t('pages.plannerList.contextMenu.upvote')}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={handleDownvote} disabled={isPending}>
              <ThumbsDown
                className={
                  planner.userVote === 'DOWN'
                    ? 'size-4 fill-current text-destructive'
                    : 'size-4'
                }
              />
              {planner.userVote === 'DOWN'
                ? t('pages.plannerList.contextMenu.removeDownvote')
                : t('pages.plannerList.contextMenu.downvote')}
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
