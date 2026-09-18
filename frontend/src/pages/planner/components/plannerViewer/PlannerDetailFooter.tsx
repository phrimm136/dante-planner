import { useRef } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowUp, GitFork, ThumbsUp } from 'lucide-react'
import { Button } from '@/components/ui/button'

import { usePlannerVote } from '../../hooks/usePlannerVote'
import { usePlannerFork } from '../../hooks/usePlannerFork'

import type { PublishedPlannerDetail } from '../../types/PlannerListTypes'

interface PlannerDetailFooterProps {
  planner: PublishedPlannerDetail
  isOwner: boolean
  isAuthenticated: boolean
}

export function PlannerDetailFooter({ planner, isAuthenticated }: PlannerDetailFooterProps) {
  const { t } = useTranslation('planner')
  const navigate = useNavigate()

  const voteMutation = usePlannerVote()
  const forkMutation = usePlannerFork()

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
      },
    )
  }

  const handleDuplicate = () => {
    forkMutation.mutate(
      { plannerId: planner.id, planner },
      {
        onSuccess: (result) => {
          void navigate({
            to: '/planner/md/$id/edit',
            params: { id: result.newPlannerId },
          }).then(() => {
            window.scrollTo({ top: 0 })
          })
        },
      },
    )
  }

  const handleBackToTop = () => {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const hasVoted = planner.hasUpvoted === true

  return (
    <footer className="flex flex-wrap items-center justify-center gap-3 pt-8 border-t">
      {isAuthenticated && (
        <Button
          variant="outline"
          onClick={handleUpvote}
          disabled={voteMutation.isPending || hasVoted}
          aria-pressed={hasVoted}
          aria-label={
            hasVoted
              ? t('pages.plannerList.contextMenu.upvoted')
              : t('pages.plannerList.contextMenu.upvote')
          }
        >
          <ThumbsUp className={hasVoted ? 'size-4 fill-current text-primary' : 'size-4'} />
          <span className="hidden lg:inline">
            {hasVoted
              ? t('pages.plannerList.contextMenu.upvoted')
              : t('pages.plannerList.contextMenu.upvote')}
          </span>
        </Button>
      )}

      <Button
        variant="outline"
        onClick={handleDuplicate}
        disabled={forkMutation.isPending}
        aria-label={t('pages.plannerList.contextMenu.copy')}
      >
        <GitFork className="size-4" />
        <span className="hidden lg:inline">{t('pages.plannerList.contextMenu.copy')}</span>
      </Button>

      <Button variant="outline" onClick={handleBackToTop} aria-label={t('pages.detail.backToTop')}>
        <ArrowUp className="size-4" />
        <span className="hidden lg:inline">{t('pages.detail.backToTop')}</span>
      </Button>
    </footer>
  )
}
