import { useState, useRef } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import {
  Eye,
  Edit,
  Copy,
  Globe,
  Trash2,
  GitFork,
  ThumbsUp,
} from 'lucide-react'

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

import { usePlannerVote } from '@/hooks/usePlannerVote'
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
 * - View, Fork, Upvote
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
  const forkMutation = usePlannerFork()

  // Double-click protection: Track if vote is in progress
  // Prevents race condition between click event and mutation.isPending becoming true
  const voteInProgressRef = useRef(false)

  const handleView = () => {
    void navigate({
      to: '/planner/md/gesellschaft/$id',
      params: { id: planner.id },
    })
    setOpen(false)
  }

  // Left-click handler for community view - navigate directly
  const handleLeftClick = (e: React.MouseEvent) => {
    // Don't navigate if clicking on interactive elements inside the card
    if ((e.target as HTMLElement).closest('button, a, [role="menuitem"]')) {
      return
    }
    void navigate({
      to: '/planner/md/gesellschaft/$id',
      params: { id: planner.id },
    })
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

  const handleUpvote = () => {
    // Double-click protection: Prevent multiple vote attempts
    if (voteInProgressRef.current) {
      return // Ignore rapid clicks
    }

    // IMMUTABLE VOTING: No toggle, vote once only
    if (planner.userVote !== null) {
      // User already voted - mutation will return 409 Conflict
      // Error handled by hook (shows toast)
    }

    voteInProgressRef.current = true
    voteMutation.mutate(
      { plannerId: planner.id, voteType: 'UP' },
      {
        onSettled: () => {
          // Reset flag after mutation completes (success or error)
          voteInProgressRef.current = false
        },
      }
    )
    setOpen(false)
  }


  const isPending = voteMutation.isPending || forkMutation.isPending

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
  // Left click = navigate, Right click = context menu
  return (
    <DropdownMenu open={open} onOpenChange={setOpen} modal={false}>
      <DropdownMenuTrigger asChild>
        <div
          onClick={handleLeftClick}
          onPointerDown={(e) => {
            // Prevent DropdownMenuTrigger from opening on left click
            // Only allow right-click (button 2) to trigger menu via onContextMenu
            if (e.button === 0) {
              e.preventDefault()
            }
          }}
          onContextMenu={(e) => {
            e.preventDefault()
            setOpen(true)
          }}
          className="cursor-pointer"
        >
          {children}
        </div>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" onCloseAutoFocus={(e) => e.preventDefault()}>
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
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={handleUpvote}
              disabled={isPending || planner.userVote !== null}
            >
              <ThumbsUp
                className={
                  planner.userVote === 'UP'
                    ? 'size-4 fill-current text-primary'
                    : 'size-4'
                }
              />
              {planner.userVote === 'UP'
                ? t('pages.plannerList.contextMenu.upvoted')
                : t('pages.plannerList.contextMenu.upvote')}
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
