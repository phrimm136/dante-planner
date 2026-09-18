import { useState, useRef } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Eye, Edit, Copy, Globe, Trash2, GitFork, ThumbsUp } from 'lucide-react'

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

import { usePlannerVote } from '../../hooks/usePlannerVote'
import { usePlannerFork } from '../../hooks/usePlannerFork'

import type { PublicPlanner, PlannerListView } from '../../types/PlannerListTypes'

interface PlannerCardContextMenuProps {
  planner: PublicPlanner
  view: PlannerListView
  isAuthenticated: boolean
  children: React.ReactNode
  onPublishToggle?: (plannerId: string) => void
  onDelete?: (plannerId: string) => void
  onDuplicate?: (plannerId: string) => void
}

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

  const voteInProgressRef = useRef(false)

  const handleView = () => {
    void navigate({
      to: '/planner/md/gesellschaft/$id',
      params: { id: planner.id },
      search: (prev) => prev,
    })
    setOpen(false)
  }

  const handleLeftClick = (e: React.MouseEvent) => {
    if ((e.target as HTMLElement).closest('button, a, [role="menuitem"]')) {
      return
    }
    void navigate({
      to: '/planner/md/gesellschaft/$id',
      params: { id: planner.id },
      search: (prev) => prev,
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
    forkMutation.mutate(
      { plannerId: planner.id },
      {
        onSuccess: (forkResult) => {
          void navigate({
            to: '/planner/md/$id/edit',
            params: { id: forkResult.newPlannerId },
          }).then(() => {
            window.scrollTo({ top: 0 })
          })
        },
      },
    )
    setOpen(false)
  }

  const handleUpvote = () => {
    if (voteInProgressRef.current) {
      return
    }

    voteInProgressRef.current = true
    voteMutation.mutate(
      { plannerId: planner.id, voteType: 'UP' },
      {
        onSettled: () => {
          voteInProgressRef.current = false
        },
      },
    )
    setOpen(false)
  }

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
          <DropdownMenuItem onClick={handleDelete} variant="destructive">
            <Trash2 className="size-4" />
            {t('pages.plannerList.contextMenu.delete')}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    )
  }

  return (
    <DropdownMenu open={open} onOpenChange={setOpen} modal={false}>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          onClick={handleLeftClick}
          onPointerDown={(e) => {
            if (e.button === 0) {
              e.preventDefault()
            }
          }}
          onContextMenu={(e) => {
            e.preventDefault()
            setOpen(true)
          }}
          className="cursor-pointer text-left"
        >
          {children}
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" onCloseAutoFocus={(e) => e.preventDefault()}>
        <DropdownMenuItem onClick={handleView}>
          <Eye className="size-4" />
          {t('pages.plannerList.contextMenu.view')}
        </DropdownMenuItem>

        {isAuthenticated && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={handleFork} disabled={forkMutation.isPending}>
              <GitFork className="size-4" />
              {t('pages.plannerList.contextMenu.copy')}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={handleUpvote}
              disabled={voteMutation.isPending || planner.hasUpvoted === true}
            >
              <ThumbsUp
                className={planner.hasUpvoted ? 'size-4 fill-current text-primary' : 'size-4'}
              />
              {planner.hasUpvoted
                ? t('pages.plannerList.contextMenu.upvoted')
                : t('pages.plannerList.contextMenu.upvote')}
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
