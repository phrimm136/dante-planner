import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import {
  Bell,
  BellOff,
  ChevronsRight,
  Edit,
  Eye,
  MessageSquare,
  Star,
  ThumbsUp,
  Trash2,
} from 'lucide-react'

import { toast } from '@/lib/toast'
import { Button } from '@/components/ui/button'
import { I18N_LOCALE_MAP, RECOMMENDED_THRESHOLD, SECTION_STYLES } from '@/lib/constants'
import { DATE_FORMATS, formatPlannerDate } from '@/lib/formatDate'
import { formatUsername } from '@/lib/formatUsername'

import { ApplyLatestMirrorDialog } from './ApplyLatestMirrorDialog'
import { CopyUrlButton } from './CopyUrlButton'
import { DeleteConfirmDialog } from './DeleteConfirmDialog'
import { ModeratorDeleteDialog } from './ModeratorDeleteDialog'
import { PlannerHeaderChrome } from './PlannerHeaderChrome'

import { useAuthQuery, isStaff } from '@/shared/auth'
import { usePlannerHeaderActions } from '../../hooks/usePlannerHeaderActions'
import { usePlannerSubscription } from '../../hooks/usePlannerSubscription'
import { useModeratorPlannerDelete } from '../../hooks/useModeratorPlannerDelete'
import { useToggleOwnerNotifications } from '../../hooks/usePlannerOwnerNotifications'
import { plannerQueryKeys } from '../../lib/plannerQueryKeys'

import type { PublishedPlannerDetail } from '../../types/PlannerListTypes'
import type { SaveablePlanner } from '../../types/PlannerTypes'

/** Delay before leaving, so the dialog's close animation is not cut off. */
const NAVIGATE_AFTER_TAKEDOWN_MS = 150

interface PublishedPlannerHeaderProps {
  /** Planner as the community sees it */
  planner: PublishedPlannerDetail
  /** Whether current user is the planner owner */
  isOwner: boolean
  /** Whether user is authenticated */
  isAuthenticated: boolean
  /** Whether sync is enabled (null = not chosen, true = enabled, false = disabled) */
  syncEnabled?: boolean | null
  /** Owner's local copy — enables Apply Latest Mirror */
  savedPlannerData?: SaveablePlanner
  /** Callback when edit is clicked */
  onEdit?: () => void
  /** Callback when delete is confirmed (optional, uses internal mutation if not provided) */
  onDelete?: () => void
  /** Callback when comment count is clicked (scrolls to comments) */
  onCommentClick?: () => void
}

/**
 * Header for a planner viewed on the community list: author, stats,
 * subscription bell, and the moderator takedown.
 */
export function PublishedPlannerHeader({
  planner,
  isOwner,
  isAuthenticated,
  syncEnabled,
  savedPlannerData,
  onEdit,
  onDelete,
  onCommentClick,
}: PublishedPlannerHeaderProps) {
  const { t, i18n } = useTranslation(['planner', 'common'])
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: user } = useAuthQuery()
  const isModerator = isStaff(user?.role)

  const [showModeratorDeleteDialog, setShowModeratorDeleteDialog] = useState(false)

  const subscriptionMutation = usePlannerSubscription()
  const moderatorDeleteMutation = useModeratorPlannerDelete()
  const ownerNotificationMutation = useToggleOwnerNotifications()

  const {
    config,
    handleBack,
    showDeleteDialog,
    setShowDeleteDialog,
    handleDeleteConfirm,
    isDeletePending,
    showApplyLatestMirrorDialog,
    setShowApplyLatestMirrorDialog,
    isApplyingLatestMirror,
    handleApplyLatestMirror,
  } = usePlannerHeaderActions({
    plannerId: planner.id,
    listRoute: '/planner/md/gesellschaft',
    plannerToUpdate: savedPlannerData,
    isAuthenticated,
    syncEnabled,
    onDelete,
  })

  const handleSubscriptionToggle = () => {
    if (planner.id) {
      subscriptionMutation.mutate(planner.id)
    }
  }

  const handleOwnerNotificationToggle = () => {
    if (planner.id) {
      ownerNotificationMutation.mutate({
        plannerId: planner.id,
        enabled: !planner.ownerNotificationsEnabled,
      })
    }
  }

  const handleModeratorDeleteConfirm = (reason: string) => {
    if (!planner.id) return

    moderatorDeleteMutation.mutate(
      { plannerId: planner.id, reason },
      {
        onSuccess: () => {
          // Invalidate planner queries to remove from cache
          void queryClient.invalidateQueries({
            queryKey: plannerQueryKeys.detail(planner.id),
          })
          void queryClient.invalidateQueries({
            queryKey: plannerQueryKeys.list(),
          })

          toast.success(t('plannerTakedown.success', { ns: 'moderation' }))
          setShowModeratorDeleteDialog(false)
          setTimeout(() => {
            void navigate({ to: '/planner/md/gesellschaft' })
          }, NAVIGATE_AFTER_TAKEDOWN_MS)
        },
        onError: () => {
          toast.error(t('plannerTakedown.failed', { ns: 'moderation' }))
        },
      },
    )
  }

  const createdAt = formatPlannerDate(
    planner.createdAt,
    I18N_LOCALE_MAP[i18n.language] ?? 'en-US',
    DATE_FORMATS.FULL_DATE_TIME_12H,
  )

  return (
    <PlannerHeaderChrome
      onBack={handleBack}
      leading={
        planner.upvotes >= RECOMMENDED_THRESHOLD ? (
          <Star className="size-5 fill-yellow-400 text-yellow-400 shrink-0" />
        ) : null
      }
      category={planner.category}
      keywords={planner.selectedKeywords ?? []}
      title={planner.title}
      meta={
        <>
          <span className="text-sm text-muted-foreground hidden lg:inline">
            {formatUsername(
              planner.authorUsernameEpithet,
              planner.authorUsernameSuffix,
              i18n.language,
            )}
          </span>
          <span className="text-sm text-muted-foreground hidden lg:inline">{createdAt ?? ''}</span>
        </>
      }
      actions={
        <>
          {isOwner &&
            savedPlannerData &&
            savedPlannerData.metadata.contentVersion < config.mdCurrentVersion && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowApplyLatestMirrorDialog(true)}
                disabled={isApplyingLatestMirror}
              >
                <ChevronsRight className="size-4" />
                <span className="hidden lg:inline">
                  {t('pages.plannerMD.applyLatestMirror.button')}
                </span>
              </Button>
            )}
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
                planner.ownerNotificationsEnabled
                  ? t('pages.detail.disableNotifications')
                  : t('pages.detail.enableNotifications')
              }
            >
              {planner.ownerNotificationsEnabled ? (
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
                planner.isSubscribed ? t('pages.detail.unsubscribe') : t('pages.detail.subscribe')
              }
              aria-pressed={planner.isSubscribed ?? false}
            >
              {planner.isSubscribed ? (
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
              <span className="hidden lg:inline">
                {t('plannerTakedown.button', { ns: 'moderation' })}
              </span>
            </Button>
          )}
        </>
      }
    >
      {/* Row 3: Stats | Copy URL */}
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-4 text-sm text-muted-foreground">
          <span className={SECTION_STYLES.LAYOUT.rowTight}>
            <Eye className="size-4" />
            {planner.viewCount}
          </span>
          <span className={SECTION_STYLES.LAYOUT.rowTight}>
            <ThumbsUp className="size-4" />
            {planner.upvotes}
          </span>
          {onCommentClick && (
            <button
              type="button"
              onClick={onCommentClick}
              className="flex items-center gap-1 hover:text-foreground transition-colors"
            >
              <MessageSquare className="size-4" />
              <span>{planner.commentCount}</span>
            </button>
          )}
        </div>
        <CopyUrlButton />
      </div>

      {/* Delete Confirmation Dialog */}
      <DeleteConfirmDialog
        open={showDeleteDialog}
        onOpenChange={setShowDeleteDialog}
        plannerId={planner.id}
        plannerTitle={planner.title || t('untitled')}
        onConfirm={handleDeleteConfirm}
        isPending={isDeletePending}
      />

      {/* Apply Latest Mirror Dialog */}
      <ApplyLatestMirrorDialog
        open={showApplyLatestMirrorDialog}
        onOpenChange={setShowApplyLatestMirrorDialog}
        onConfirm={() => {
          void handleApplyLatestMirror()
        }}
        isPending={isApplyingLatestMirror}
      />

      {/* Moderator Delete Dialog */}
      <ModeratorDeleteDialog
        open={showModeratorDeleteDialog}
        onOpenChange={setShowModeratorDeleteDialog}
        plannerTitle={planner.title || t('untitled')}
        onConfirm={handleModeratorDeleteConfirm}
        isPending={moderatorDeleteMutation.isPending}
      />
    </PlannerHeaderChrome>
  )
}
