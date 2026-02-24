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
  Star,
  ThumbsUp,
  Trash2,
  Upload,
} from 'lucide-react'

import { toast } from 'sonner'

import { BannedError, TimedOutError } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { CopyUrlButton } from './CopyUrlButton'
import { DeleteConfirmDialog } from './DeleteConfirmDialog'
import { ModeratorDeleteDialog } from './ModeratorDeleteDialog'
import { PublishSyncOffWarningDialog } from './PublishSyncOffWarningDialog'

import { useAuthQuery } from '@/hooks/useAuthQuery'
import { usePlannerSubscription } from '@/hooks/usePlannerSubscription'
import { usePlannerDelete } from '@/hooks/usePlannerDelete'
import { useModeratorPlannerDelete } from '@/hooks/useModeratorPlannerDelete'
import { usePlannerPublish } from '@/hooks/usePlannerPublish'
import { useToggleOwnerNotifications } from '@/hooks/usePlannerOwnerNotifications'
import { usePlannerStorage } from '@/hooks/usePlannerStorage'
import { plannerQueryKeys } from '@/hooks/useSavedPlannerQuery'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { usePlannerSyncAdapter } from '@/hooks/usePlannerSyncAdapter'
import { useQueryClient } from '@tanstack/react-query'
import { formatUsername } from '@/lib/formatUsername'
import { getKeywordIconPath } from '@/lib/assetPaths'
import { I18N_LOCALE_MAP, MD_CATEGORY_COLORS, MD_CATEGORY_TEXT_COLORS, RECOMMENDED_THRESHOLD } from '@/lib/constants'
import { validatePlannerForSave, toUserFriendlyError } from '@/lib/plannerHelpers'

import type { MDCategory } from '@/lib/constants'
import type { PublishedPlannerDetail } from '@/types/PlannerListTypes'
import type { SaveablePlanner, MDPlannerContent } from '@/types/PlannerTypes'

interface PlannerDetailHeaderProps {
  /** Header variant based on viewing context */
  variant: 'published' | 'personal'
  /** Planner data - PublishedPlannerDetail for published, SaveablePlanner for personal */
  planner: PublishedPlannerDetail | SaveablePlanner
  /** Whether current user is the planner owner */
  isOwner: boolean
  /** Whether user is authenticated */
  isAuthenticated: boolean
  /** Whether sync is enabled (null = not chosen, true = enabled, false = disabled) */
  syncEnabled?: boolean | null
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
  syncEnabled,
  onEdit,
  onDelete,
  onCommentClick,
}: PlannerDetailHeaderProps) {
  const { t, i18n } = useTranslation(['planner', 'common'])
  const navigate = useNavigate()

  const { data: user } = useAuthQuery()
  const isModerator = user?.role === 'MODERATOR' || user?.role === 'ADMIN'

  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [showModeratorDeleteDialog, setShowModeratorDeleteDialog] = useState(false)
  const [showPublishWarning, setShowPublishWarning] = useState(false)
  const [isUploadingForPublish, setIsUploadingForPublish] = useState(false)

  const subscriptionMutation = usePlannerSubscription()
  const deleteMutation = usePlannerDelete()
  const moderatorDeleteMutation = useModeratorPlannerDelete()
  const publishMutation = usePlannerPublish()
  const ownerNotificationMutation = useToggleOwnerNotifications()
  const { savePlanner } = usePlannerStorage()
  const syncAdapter = usePlannerSyncAdapter()
  const queryClient = useQueryClient()
  const { spec: egoGiftSpec, i18n: egoGiftI18n } = useEGOGiftListData()

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

  const handleOwnerNotificationToggle = () => {
    if (plannerId && publishedPlanner) {
      ownerNotificationMutation.mutate({
        plannerId,
        enabled: !(publishedPlanner.ownerNotificationsEnabled),
      })
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

  const handleModeratorDeleteConfirm = (reason: string) => {
    if (!plannerId) return

    moderatorDeleteMutation.mutate(
      { plannerId, reason },
      {
        onSuccess: () => {
          // Invalidate planner queries to remove from cache
          void queryClient.invalidateQueries({
            queryKey: plannerQueryKeys.detail(plannerId),
          })
          void queryClient.invalidateQueries({
            queryKey: plannerQueryKeys.list(),
          })

          toast.success(t('plannerTakedown.success', { ns: 'moderation' }))
          setShowModeratorDeleteDialog(false)
          setTimeout(() => {
            void navigate({ to: '/planner/md/gesellschaft' })
          }, 150)
        },
        onError: () => {
          toast.error(t('plannerTakedown.failed', { ns: 'moderation' }))
        },
      }
    )
  }

  const handlePublishToggle = () => {
    if (!plannerId || !savedPlanner) return

    // Only validate when publishing (not unpublishing)
    if (!savedPlanner.metadata.published && savedPlanner.config.type === 'MIRROR_DUNGEON') {
      const content = savedPlanner.content as MDPlannerContent
      const category = savedPlanner.config.category as MDCategory
      const { isValid, errors } = validatePlannerForSave(
        savedPlanner.metadata.title,
        content,
        category,
        egoGiftSpec,
        egoGiftI18n
      )
      if (!isValid) {
        const friendly = toUserFriendlyError(errors[0])
        toast.error(t(friendly.key, friendly.params))
        return
      }
    }

    // Check if sync is disabled and user is trying to publish (not unpublish)
    if (syncEnabled === false && !savedPlanner.metadata.published) {
      // Show warning dialog for sync-off publish
      setShowPublishWarning(true)
      return
    }

    // Normal publish flow (sync enabled or unpublishing)
    publishMutation.mutate(plannerId, {
      onSuccess: async (response) => {
        // Update local storage with new publish state
        const updatedPlanner: SaveablePlanner = {
          ...savedPlanner,
          metadata: {
            ...savedPlanner.metadata,
            published: response.published,
          },
        }
        await savePlanner(updatedPlanner)

        // Invalidate query to refetch updated planner
        void queryClient.invalidateQueries({
          queryKey: plannerQueryKeys.detail(plannerId),
        })

        const wasPublished = savedPlanner.metadata.published
        toast.success(
          t(wasPublished ? 'pages.plannerMD.publish.unpublishSuccess' : 'pages.plannerMD.publish.success')
        )
      },
      onError: (error) => {
        if (error instanceof BannedError) {
          toast.error(t('moderation.banned', { ns: 'common' }))
        } else if (error instanceof TimedOutError) {
          toast.error(t('moderation.timedOut', { ns: 'common' }))
        } else {
          toast.error(t('pages.plannerMD.publish.failed'))
        }
      },
    })
  }

  const handlePublishWithUpload = async () => {
    if (!plannerId || !savedPlanner) return

    setIsUploadingForPublish(true)
    setShowPublishWarning(false)

    try {
      // Validate before upload (strict: title + theme packs required)
      const content = savedPlanner.content as MDPlannerContent
      const category = savedPlanner.config.category as MDCategory
      const { isValid, errors } = validatePlannerForSave(
        savedPlanner.metadata.title,
        content,
        category,
        egoGiftSpec,
        egoGiftI18n
      )
      if (!isValid) {
        const friendly = toUserFriendlyError(errors[0])
        toast.error(t(friendly.key, friendly.params))
        setIsUploadingForPublish(false)
        return
      }

      // Step 1: Upload plan to server (force sync even though sync is disabled)
      await syncAdapter.syncToServer(savedPlanner)

      // Step 2: Call publish API
      publishMutation.mutate(plannerId, {
        onSuccess: async (response) => {
          // Update local storage with new publish state
          const updatedPlanner: SaveablePlanner = {
            ...savedPlanner,
            metadata: {
              ...savedPlanner.metadata,
              published: response.published,
            },
          }
          await savePlanner(updatedPlanner)

          // Invalidate query to refetch updated planner
          void queryClient.invalidateQueries({
            queryKey: plannerQueryKeys.detail(plannerId),
          })

          toast.success(t('pages.plannerMD.publish.success'))
          setIsUploadingForPublish(false)
        },
        onError: (error) => {
          if (error instanceof BannedError) {
            toast.error(t('moderation.banned', { ns: 'common' }))
          } else if (error instanceof TimedOutError) {
            toast.error(t('moderation.timedOut', { ns: 'common' }))
          } else {
            toast.error(t('pages.plannerMD.publish.failed'))
          }
          setIsUploadingForPublish(false)
        },
      })
    } catch (error) {
      console.error('Failed to upload plan for publishing:', error)
      toast.error(t('pages.plannerMD.publish.uploadFailed'))
      setIsUploadingForPublish(false)
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
        {/* Row 1: Back, Star, Category, Keywords | Author, Date */}
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-3 min-w-0">
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={handleBack}
              aria-label={t('pages.detail.backToList')}
              className="shrink-0"
            >
              <ArrowLeft className="size-4" />
            </Button>
            {publishedPlanner.upvotes >= RECOMMENDED_THRESHOLD && (
              <Star className="size-5 fill-yellow-400 text-yellow-400 shrink-0" />
            )}
            <span
              className="px-2 py-0.5 text-sm font-medium rounded shrink-0"
              style={{
                backgroundColor: publishedPlanner.category in MD_CATEGORY_COLORS
                  ? MD_CATEGORY_COLORS[publishedPlanner.category as MDCategory]
                  : undefined,
                color: publishedPlanner.category in MD_CATEGORY_TEXT_COLORS
                  ? MD_CATEGORY_TEXT_COLORS[publishedPlanner.category as MDCategory]
                  : undefined,
              }}
            >
              {t(`pages.plannerList.mdCategory.${publishedPlanner.category}`)}
            </span>
            {publishedPlanner.selectedKeywords && publishedPlanner.selectedKeywords.length > 0 && (
              <div className="flex items-center gap-1.5 overflow-x-auto">
                {publishedPlanner.selectedKeywords.map((keyword) => (
                  <img
                    key={keyword}
                    src={getKeywordIconPath(keyword)}
                    alt={keyword}
                    className="size-6 object-contain shrink-0"
                  />
                ))}
              </div>
            )}
          </div>

          <div className="flex items-center gap-3 shrink-0">
            <span className="text-sm text-muted-foreground hidden lg:inline">
              {formatUsername(
                publishedPlanner.authorUsernameEpithet,
                publishedPlanner.authorUsernameSuffix,
                i18n.language
              )}
            </span>
            <span className="text-sm text-muted-foreground hidden lg:inline">
              {formatDate(publishedPlanner.createdAt)}
            </span>
          </div>
        </div>

        {/* Row 2: Title | Actions */}
        <div className="flex items-center justify-between gap-4">
          <h1 className="text-2xl font-bold">{publishedPlanner.title || t('untitled')}</h1>

          <div className="flex items-center gap-1 shrink-0">
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
            {isOwner && (
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={handleOwnerNotificationToggle}
                disabled={ownerNotificationMutation.isPending}
                aria-label={
                  publishedPlanner.ownerNotificationsEnabled
                    ? t('pages.detail.disableNotifications')
                    : t('pages.detail.enableNotifications')
                }
              >
                {publishedPlanner.ownerNotificationsEnabled ? (
                  <Bell className="size-4 fill-current text-primary" />
                ) : (
                  <BellOff className="size-4 text-muted-foreground" />
                )}
              </Button>
            )}
            {isAuthenticated && !isOwner && (
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
            {isModerator && !isOwner && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowModeratorDeleteDialog(true)}
                className="text-destructive hover:text-destructive"
              >
                <Trash2 className="size-4" />
                <span className="hidden lg:inline">{t('plannerTakedown.button', { ns: 'moderation' })}</span>
              </Button>
            )}
          </div>
        </div>

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
                <span>{publishedPlanner.commentCount}</span>
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

        {/* Moderator Delete Dialog */}
        <ModeratorDeleteDialog
          open={showModeratorDeleteDialog}
          onOpenChange={setShowModeratorDeleteDialog}
          plannerTitle={publishedPlanner.title || t('untitled')}
          onConfirm={handleModeratorDeleteConfirm}
          isPending={moderatorDeleteMutation.isPending}
        />
      </header>
    )
  }

  // Personal variant
  if (savedPlanner) {
    // Determine detailed status (same logic as PersonalPlannerCard)
    let status: 'draft' | 'saved' | 'unsynced' | 'synced' | 'published' | 'unpublishedChanges' = 'draft'
    let statusBadgeVariant: 'default' | 'secondary' | 'outline' | 'destructive' = 'outline'

    if (savedPlanner.metadata.published) {
      if (savedPlanner.metadata.status === 'draft') {
        status = 'unpublishedChanges'
        statusBadgeVariant = 'destructive'
      } else {
        status = 'published'
        statusBadgeVariant = 'default'
      }
    } else if (isAuthenticated && syncEnabled === true) {
      // Authenticated with sync enabled
      if (savedPlanner.metadata.status === 'draft' || savedPlanner.metadata.savedAt === null) {
        status = 'unsynced'
        statusBadgeVariant = 'secondary'
      } else {
        status = 'synced'
        statusBadgeVariant = 'default'
      }
    } else {
      // Guest or sync disabled
      if (savedPlanner.metadata.status === 'draft' || savedPlanner.metadata.savedAt === null) {
        status = 'draft'
        statusBadgeVariant = 'secondary'
      } else {
        status = 'saved'
        statusBadgeVariant = 'default'
      }
    }

    const statusLabels = {
      draft: t('pages.detail.status.draft'),
      saved: t('pages.detail.status.saved'),
      unsynced: t('pages.detail.status.unsynced'),
      synced: t('pages.detail.status.synced'),
      published: t('pages.detail.status.published'),
      unpublishedChanges: t('pages.detail.status.unpublishedChanges'),
    }

    const statusLabel = statusLabels[status]

    return (
      <header className="space-y-3">
        {/* Row 1: Back, Category, Keywords | Status, Last edited */}
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-3 min-w-0">
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={handleBack}
              aria-label={t('pages.detail.backToList')}
              className="shrink-0"
            >
              <ArrowLeft className="size-4" />
            </Button>
            <span
              className="px-2 py-0.5 text-sm font-medium rounded shrink-0"
              style={{
                backgroundColor: savedPlanner.config.category in MD_CATEGORY_COLORS
                  ? MD_CATEGORY_COLORS[savedPlanner.config.category as MDCategory]
                  : undefined,
                color: savedPlanner.config.category in MD_CATEGORY_TEXT_COLORS
                  ? MD_CATEGORY_TEXT_COLORS[savedPlanner.config.category as MDCategory]
                  : undefined,
              }}
            >
              {t(`pages.plannerList.mdCategory.${savedPlanner.config.category}`)}
            </span>
            {'selectedKeywords' in savedPlanner.content &&
              savedPlanner.content.selectedKeywords.length > 0 && (
                <div className="flex items-center gap-1.5 overflow-x-auto">
                  {savedPlanner.content.selectedKeywords.map((keyword) => (
                    <img
                      key={keyword}
                      src={getKeywordIconPath(keyword)}
                      alt={keyword}
                      className="size-6 object-contain shrink-0"
                    />
                  ))}
                </div>
              )}
          </div>

          <div className="flex items-center gap-3 shrink-0">
            <Badge variant={statusBadgeVariant}>{statusLabel}</Badge>
            <span className="text-sm text-muted-foreground hidden lg:inline">
              {t('pages.detail.lastEdited', {
                date: formatDate(savedPlanner.metadata.lastModifiedAt),
              })}
            </span>
          </div>
        </div>

        {/* Row 2: Title | Actions */}
        <div className="flex items-center justify-between gap-4">
          <h1 className="text-2xl font-bold">
            {savedPlanner.metadata.title || t('untitled')}
          </h1>

          <div className="flex items-center gap-1 shrink-0">
            {onEdit && (
              <Button variant="outline" size="sm" onClick={onEdit}>
                <Edit className="size-4" />
                <span className="hidden lg:inline">{t('pages.plannerList.contextMenu.edit')}</span>
              </Button>
            )}
            {isAuthenticated && (
              <Button
                variant="outline"
                size="sm"
                onClick={handlePublishToggle}
                disabled={publishMutation.isPending}
              >
                <Upload className="size-4" />
                <span className="hidden lg:inline">
                  {publishMutation.isPending
                    ? t(savedPlanner.metadata.published ? 'pages.plannerMD.publish.unpublishing' : 'pages.plannerMD.publish.publishing')
                    : savedPlanner.metadata.published
                      ? t('pages.plannerMD.publish.unpublish')
                      : t('pages.plannerMD.publish.button')}
                </span>
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

        {/* Delete Confirmation Dialog */}
        <DeleteConfirmDialog
          open={showDeleteDialog}
          onOpenChange={setShowDeleteDialog}
          plannerId={savedPlanner.metadata.id}
          plannerTitle={savedPlanner.metadata.title || t('untitled')}
          onConfirm={handleDeleteConfirm}
          isPending={deleteMutation.isPending}
        />

        {/* Publish Sync-Off Warning Dialog */}
        <PublishSyncOffWarningDialog
          open={showPublishWarning}
          onOpenChange={setShowPublishWarning}
          onConfirm={handlePublishWithUpload}
          isPending={isUploadingForPublish || publishMutation.isPending}
        />
      </header>
    )
  }

  return null
}
