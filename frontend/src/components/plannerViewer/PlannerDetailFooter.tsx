import { useRef } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowUp, Flag, GitFork, ThumbsUp } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'

import { usePlannerVote } from '@/hooks/usePlannerVote'
import { usePlannerFork } from '@/hooks/usePlannerFork'
import { usePlannerReport } from '@/hooks/usePlannerReport'

import type { PublishedPlannerDetail } from '@/types/PlannerListTypes'

interface PlannerDetailFooterProps {
  /** Published planner data */
  planner: PublishedPlannerDetail
  /** Whether current user is the planner owner */
  isOwner: boolean
  /** Whether user is authenticated */
  isAuthenticated: boolean
}

/**
 * Footer component for published planner detail page.
 * Contains engagement actions: Upvote, Duplicate, Report, Back to Top.
 *
 * - Upvote: Disabled after voting (immutable)
 * - Duplicate: Hidden for owner, creates copy in user's drafts
 * - Report: Hidden for owner, disabled after reporting
 * - Back to Top: Always visible
 *
 * @example
 * <PlannerDetailFooter
 *   planner={publishedPlanner}
 *   isOwner={false}
 *   isAuthenticated={true}
 * />
 */
export function PlannerDetailFooter({
  planner,
  isOwner,
  isAuthenticated,
}: PlannerDetailFooterProps) {
  const { t } = useTranslation('planner')
  const navigate = useNavigate()

  const voteMutation = usePlannerVote()
  const forkMutation = usePlannerFork()
  const reportMutation = usePlannerReport()

  // Double-click protection for vote
  const voteInProgressRef = useRef(false)

  const handleUpvote = () => {
    if (!isAuthenticated) return
    if (voteInProgressRef.current) return
    if (planner.hasUpvoted) return

    voteInProgressRef.current = true
    voteMutation.mutate(
      { plannerId: planner.id, voteType: 'UP' },
      {
        onSettled: () => {
          voteInProgressRef.current = false
        },
      }
    )
  }

  const handleDuplicate = () => {
    if (!isAuthenticated || isOwner) return

    forkMutation.mutate(planner.id, {
      onSuccess: (result) => {
        void navigate({
          to: '/planner/md/$id/edit',
          params: { id: result.newPlannerId },
        })
      },
    })
  }

  const handleReport = () => {
    if (!isAuthenticated || isOwner) return
    if (planner.hasReported) return

    reportMutation.mutate(planner.id, {
      onSuccess: () => {
        toast.success(t('pages.detail.reportSubmitted'))
      },
    })
  }

  const handleBackToTop = () => {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const isPending = voteMutation.isPending || forkMutation.isPending || reportMutation.isPending
  const hasVoted = planner.hasUpvoted === true
  const hasReported = planner.hasReported === true

  return (
    <footer className="flex flex-wrap items-center justify-center gap-3 pt-8 border-t">
      {/* Upvote */}
      {isAuthenticated && (
        <Button
          variant="outline"
          onClick={handleUpvote}
          disabled={isPending || hasVoted}
          aria-pressed={hasVoted}
        >
          <ThumbsUp
            className={hasVoted ? 'size-4 fill-current text-primary' : 'size-4'}
          />
          <span className="hidden lg:inline">
            {hasVoted
              ? t('pages.plannerList.contextMenu.upvoted')
              : t('pages.plannerList.contextMenu.upvote')}
          </span>
        </Button>
      )}

      {/* Duplicate (Fork) - Hidden for owner */}
      {isAuthenticated && !isOwner && (
        <Button
          variant="outline"
          onClick={handleDuplicate}
          disabled={isPending}
        >
          <GitFork className="size-4" />
          <span className="hidden lg:inline">
            {t('pages.plannerList.contextMenu.duplicate')}
          </span>
        </Button>
      )}

      {/* Report - Hidden for owner */}
      {isAuthenticated && !isOwner && (
        <Button
          variant="ghost"
          onClick={handleReport}
          disabled={isPending || hasReported}
        >
          <Flag className="size-4" />
          <span className="hidden lg:inline">
            {hasReported
              ? t('pages.detail.reported')
              : t('pages.detail.report')}
          </span>
        </Button>
      )}

      {/* Back to Top - Always visible */}
      <Button variant="outline" onClick={handleBackToTop}>
        <ArrowUp className="size-4" />
        <span className="hidden lg:inline">{t('pages.detail.backToTop')}</span>
      </Button>
    </footer>
  )
}
