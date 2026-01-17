import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import {
  ArrowLeft,
  Bell,
  BellOff,
  Edit,
  Eye,
  MessageSquare,
  ThumbsUp,
  Trash2,
} from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { CopyUrlButton } from './CopyUrlButton'
import { DeleteConfirmDialog } from './DeleteConfirmDialog'

import { usePlannerSubscription } from '@/hooks/usePlannerSubscription'
import { usePlannerDelete } from '@/hooks/usePlannerDelete'
import { formatUsername } from '@/lib/formatUsername'
import { I18N_LOCALE_MAP } from '@/lib/constants'

import type { PublishedPlannerDetail } from '@/types/PlannerListTypes'
import type { SaveablePlanner } from '@/types/PlannerTypes'

interface PlannerDetailHeaderProps {
  /** Header variant based on viewing context */
  variant: 'published' | 'personal'
  /** Planner data - PublishedPlannerDetail for published, SaveablePlanner for personal */
  planner: PublishedPlannerDetail | SaveablePlanner
  /** Whether current user is the planner owner */
  isOwner: boolean
  /** Whether user is authenticated */
  isAuthenticated: boolean
  /** Callback when edit is clicked */
  onEdit?: () => void
  /** Callback when delete is confirmed (optional, uses internal mutation if not provided) */
  onDelete?: () => void
  /** Callback when comment count is clicked (scrolls to comments) */
  onCommentClick?: () => void
}

/**
 * Header component for planner detail pages.
 * Two variants:
 * - 'published': Full header with author, stats, subscription bell
 * - 'personal': Minimal header with last edited date and status badge
 *
 * @example
 * // Published view (community)
 * <PlannerDetailHeader
 *   variant="published"
 *   planner={publishedPlanner}
 *   isOwner={false}
 *   isAuthenticated={true}
 * />
 *
 * // Personal view (my plans)
 * <PlannerDetailHeader
 *   variant="personal"
 *   planner={savedPlanner}
 *   isOwner={true}
 *   isAuthenticated={true}
 *   onEdit={() => navigate({ to: '/planner/md/$id/edit', params: { id } })}
 * />
 */
export function PlannerDetailHeader({
  variant,
  planner,
  isOwner,
  isAuthenticated,
  onEdit,
  onDelete,
  onCommentClick,
}: PlannerDetailHeaderProps) {
  const { t, i18n } = useTranslation(['planner', 'common'])
  const navigate = useNavigate()

  const [showDeleteDialog, setShowDeleteDialog] = useState(false)

  const subscriptionMutation = usePlannerSubscription()
  const deleteMutation = usePlannerDelete()

  // Type guards
  const isPublished = variant === 'published'
  const publishedPlanner = isPublished ? (planner as PublishedPlannerDetail) : null
  const savedPlanner = !isPublished ? (planner as SaveablePlanner) : null

  // Get planner ID
  const plannerId = isPublished ? publishedPlanner?.id : savedPlanner?.metadata.id

  const handleBack = () => {
    void navigate({ to: isPublished ? '/planner/md/gesellschaft' : '/planner/md' })
  }

  const handleSubscriptionToggle = () => {
    if (plannerId) {
      subscriptionMutation.mutate(plannerId)
    }
  }

  const handleDeleteConfirm = () => {
    if (onDelete) {
      onDelete()
    } else if (plannerId) {
      deleteMutation.mutate(plannerId, {
        onSuccess: () => {
          // Close dialog first, then navigate after animation completes
          setShowDeleteDialog(false)
          setTimeout(() => {
            void navigate({ to: isPublished ? '/planner/md/gesellschaft' : '/planner/md' })
          }, 150)
        },
      })
    }
  }

  // Format dates with i18n locale
  const formatDate = (dateString: string | null | undefined) => {
    if (!dateString) return ''
    const locale = I18N_LOCALE_MAP[i18n.language] ?? 'en-US'
    return new Date(dateString).toLocaleString(locale, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
      hour12: true,
    })
  }

  if (isPublished && publishedPlanner) {
    return (
      <header className="space-y-3">
        {/* Row 1: Back, Author, Date | Actions */}
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={handleBack}
              aria-label={t('pages.detail.backToList')}
            >
              <ArrowLeft className="size-4" />
            </Button>
            <span className="text-sm text-muted-foreground">
              {formatUsername(
                publishedPlanner.authorUsernameKeyword,
                publishedPlanner.authorUsernameSuffix
              )}
            </span>
            <span className="text-sm text-muted-foreground">
              {formatDate(publishedPlanner.createdAt)}
            </span>
          </div>

          <div className="flex items-center gap-1">
            {isOwner && onEdit && (
              <Button variant="outline" size="sm" onClick={onEdit}>
                <Edit className="size-4" />
                <span className="hidden lg:inline">{t('pages.plannerList.contextMenu.edit')}</span>
              </Button>
            )}
            {isOwner && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowDeleteDialog(true)}
                className="text-destructive hover:text-destructive"
              >
                <Trash2 className="size-4" />
                <span className="hidden lg:inline">{t('pages.plannerList.contextMenu.delete')}</span>
              </Button>
            )}
            {isAuthenticated && (
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={handleSubscriptionToggle}
                disabled={subscriptionMutation.isPending}
                aria-label={
                  publishedPlanner.isSubscribed
                    ? t('pages.detail.unsubscribe')
                    : t('pages.detail.subscribe')
                }
                aria-pressed={publishedPlanner.isSubscribed ?? false}
              >
                {publishedPlanner.isSubscribed ? (
                  <Bell className="size-4 fill-current text-primary" />
                ) : (
                  <BellOff className="size-4 text-muted-foreground" />
                )}
              </Button>
            )}
          </div>
        </div>

        {/* Row 2: Title */}
        <h1 className="text-2xl font-bold">{publishedPlanner.title || t('untitled')}</h1>

        {/* Row 3: Stats | Copy URL */}
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-4 text-sm text-muted-foreground">
            <span className="flex items-center gap-1">
              <Eye className="size-4" />
              {publishedPlanner.viewCount}
            </span>
            <span className="flex items-center gap-1">
              <ThumbsUp className="size-4" />
              {publishedPlanner.upvotes}
            </span>
            {onCommentClick && (
              <button
                type="button"
                onClick={onCommentClick}
                className="flex items-center gap-1 hover:text-foreground transition-colors"
              >
                <MessageSquare className="size-4" />
                <span>0</span>
              </button>
            )}
          </div>
          <CopyUrlButton />
        </div>

        {/* Delete Confirmation Dialog */}
        <DeleteConfirmDialog
          open={showDeleteDialog}
          onOpenChange={setShowDeleteDialog}
          plannerId={publishedPlanner.id}
          plannerTitle={publishedPlanner.title || t('untitled')}
          onConfirm={handleDeleteConfirm}
          isPending={deleteMutation.isPending}
        />
      </header>
    )
  }

  // Personal variant
  if (savedPlanner) {
    const statusBadgeVariant = savedPlanner.metadata.published
      ? 'default'
      : savedPlanner.metadata.status === 'saved'
        ? 'secondary'
        : 'outline'

    const statusLabel = savedPlanner.metadata.published
      ? t('pages.detail.status.published')
      : savedPlanner.metadata.status === 'saved'
        ? t('pages.detail.status.saved')
        : t('pages.detail.status.draft')

    return (
      <header className="space-y-3">
        {/* Row 1: Back, Last edited | Actions */}
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={handleBack}
              aria-label={t('pages.detail.backToList')}
            >
              <ArrowLeft className="size-4" />
            </Button>
            <span className="text-sm text-muted-foreground">
              {t('pages.detail.lastEdited', {
                date: formatDate(savedPlanner.metadata.lastModifiedAt),
              })}
            </span>
          </div>

          <div className="flex items-center gap-1">
            {onEdit && (
              <Button variant="outline" size="sm" onClick={onEdit}>
                <Edit className="size-4" />
                <span className="hidden lg:inline">{t('pages.plannerList.contextMenu.edit')}</span>
              </Button>
            )}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setShowDeleteDialog(true)}
              className="text-destructive hover:text-destructive"
            >
              <Trash2 className="size-4" />
              <span className="hidden lg:inline">{t('pages.plannerList.contextMenu.delete')}</span>
            </Button>
          </div>
        </div>

        {/* Row 2: Title + Status Badge */}
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold">
            {savedPlanner.metadata.title || t('untitled')}
          </h1>
          <Badge variant={statusBadgeVariant}>{statusLabel}</Badge>
        </div>

        {/* Delete Confirmation Dialog */}
        <DeleteConfirmDialog
          open={showDeleteDialog}
          onOpenChange={setShowDeleteDialog}
          plannerId={savedPlanner.metadata.id}
          plannerTitle={savedPlanner.metadata.title || t('untitled')}
          onConfirm={handleDeleteConfirm}
          isPending={deleteMutation.isPending}
        />
      </header>
    )
  }

  return null
}
