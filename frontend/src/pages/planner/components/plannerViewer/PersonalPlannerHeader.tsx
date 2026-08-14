import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronsRight, Edit, Trash2, Upload } from 'lucide-react'

import { toast } from '@/lib/toast'
import { assertNever } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { I18N_LOCALE_MAP } from '@/lib/constants'
import { DATE_FORMATS, formatPlannerDate } from '@/lib/formatDate'

import { ApplyLatestMirrorDialog } from './ApplyLatestMirrorDialog'
import { DeleteConfirmDialog } from './DeleteConfirmDialog'
import { PlannerHeaderChrome } from './PlannerHeaderChrome'
import { SyncOffWarningDialog } from '../SyncOffWarningDialog'

import { usePlannerHeaderActions } from '../../hooks/usePlannerHeaderActions'
import { usePlannerPublish } from '../../hooks/usePlannerPublish'
import { usePlannerStorage } from '../../hooks/usePlannerStorage'
import { usePlannerSyncAdapter } from '../../hooks/usePlannerSyncAdapter'
import { useEGOGiftListData } from '@/pages/egoGift'
import { plannerQueryKeys } from '../../lib/plannerQueryKeys'
import { deriveSaveStatus, SAVE_STATUS_BADGE_VARIANT } from '../../lib/plannerBadges'
import { decidePublishAction } from '../../lib/plannerPublishPolicy'
import { toastForError } from '../../lib/plannerSaveErrors'
import { validatePlannerForPublish } from '../../lib/plannerValidation'
import { toUserFriendlyError } from '../../lib/plannerValidationErrors'

import { isMDPlanner } from '../../types/PlannerTypes'
import type { SaveablePlanner } from '../../types/PlannerTypes'

/** Publish-button label, by mutation state and by which direction the toggle goes. */
const PUBLISH_LABEL_KEYS = {
  idle: {
    publish: 'pages.plannerMD.publish.button',
    unpublish: 'pages.plannerMD.publish.unpublish',
  },
  pending: {
    publish: 'pages.plannerMD.publish.publishing',
    unpublish: 'pages.plannerMD.publish.unpublishing',
  },
} as const

interface PersonalPlannerHeaderProps {
  /** The user's own copy of the planner */
  planner: SaveablePlanner
  /** Whether user is authenticated */
  isAuthenticated: boolean
  /** Whether sync is enabled (null = not chosen, true = enabled, false = disabled) */
  syncEnabled?: boolean | null
  /** Callback when edit is clicked */
  onEdit?: () => void
  /** Callback when delete is confirmed (optional, uses internal mutation if not provided) */
  onDelete?: () => void
}

/**
 * Header for a planner viewed in "My Plans": save status, last edit time, and
 * the publish toggle.
 */
export function PersonalPlannerHeader({
  planner,
  isAuthenticated,
  syncEnabled,
  onEdit,
  onDelete,
}: PersonalPlannerHeaderProps) {
  const { t, i18n } = useTranslation(['planner', 'common'])
  const queryClient = useQueryClient()

  const [showPublishWarning, setShowPublishWarning] = useState(false)
  const [isUploadingForPublish, setIsUploadingForPublish] = useState(false)

  const publishMutation = usePlannerPublish()
  const { saveToLocal } = usePlannerStorage()
  const syncAdapter = usePlannerSyncAdapter()
  const { spec: egoGiftSpec, i18n: egoGiftI18n } = useEGOGiftListData()

  const plannerId = planner.metadata.id

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
    plannerId,
    listRoute: '/planner/md',
    plannerToUpdate: planner,
    isAuthenticated,
    syncEnabled,
    onDelete,
  })

  // `base` is the planner the local save is derived from: for publish it's the
  // server-synced planner (carries the server-bumped syncVersion); for unpublish
  // it's the current planner (unpublishing never bumps syncVersion).
  const callPublishMutation = (wasPublished: boolean, base: SaveablePlanner) => {
    if (!plannerId) return

    publishMutation.mutate(
      { plannerId, published: !wasPublished },
      {
        onSuccess: async (response) => {
          const updatedPlanner: SaveablePlanner = {
            ...base,
            metadata: {
              ...base.metadata,
              published: response.published,
            },
          }
          // Server toggle already succeeded; the local mirror is best-effort. Surface
          // a local-save failure instead of swallowing it (the personal view reads
          // from IndexedDB, so a failed save leaves it stale until the next save).
          const saveResult = await saveToLocal(updatedPlanner)
          if (!saveResult.ok) {
            console.error('Local planner save failed after publish toggle:', saveResult.error.kind)
          }

          void queryClient.invalidateQueries({
            queryKey: plannerQueryKeys.detail(plannerId),
          })

          toast.success(
            t(
              wasPublished
                ? 'pages.plannerMD.publish.unpublishSuccess'
                : 'pages.plannerMD.publish.success',
            ),
          )
          setIsUploadingForPublish(false)
        },
        onError: (error) => {
          toastForError(error, 'pages.plannerMD.publish.failed')
          setIsUploadingForPublish(false)
        },
      },
    )
  }

  const handlePublishWithUpload = async () => {
    if (!plannerId) return

    setIsUploadingForPublish(true)
    setShowPublishWarning(false)

    try {
      // Upload current content first (the publish PUT carries none), then toggle.
      // The synced result carries the server-bumped syncVersion — thread it into
      // the local save so the next toggle doesn't send a stale version (409).
      const synced = await syncAdapter.syncToServer(planner)
      callPublishMutation(false, synced.planner)
    } catch (error) {
      console.error('Failed to upload plan for publishing:', error)
      toast.error(t('pages.plannerMD.publish.uploadFailed'))
      setIsUploadingForPublish(false)
    }
  }

  const publishValidationErrors = () =>
    isMDPlanner(planner)
      ? validatePlannerForPublish(
          planner.metadata.title,
          planner.content,
          planner.config.category,
          egoGiftSpec,
          egoGiftI18n,
        ).errors
      : []

  const handlePublishToggle = () => {
    if (!plannerId) return

    const isPublished = planner.metadata.published
    const action = decidePublishAction({
      isPublished,
      validationErrors: isPublished ? [] : publishValidationErrors(),
      syncEnabled,
    })

    switch (action.kind) {
      case 'unpublish':
        callPublishMutation(true, planner)
        return
      case 'invalid': {
        const friendly = toUserFriendlyError(action.error)
        toast.error(t(friendly.key, friendly.params))
        return
      }
      case 'warnSyncDisabled':
        setShowPublishWarning(true)
        return
      case 'uploadThenPublish':
        void handlePublishWithUpload()
        return
      default:
        assertNever(action)
    }
  }

  const status = deriveSaveStatus(planner.metadata, isAuthenticated, syncEnabled)
  const lastEditedAt = formatPlannerDate(
    planner.metadata.lastModifiedAt,
    I18N_LOCALE_MAP[i18n.language] ?? 'en-US',
    DATE_FORMATS.FULL_DATE_TIME_12H,
  )

  return (
    <PlannerHeaderChrome
      onBack={handleBack}
      category={planner.config.category}
      keywords={'selectedKeywords' in planner.content ? planner.content.selectedKeywords : []}
      title={planner.metadata.title}
      meta={
        <>
          <Badge variant={SAVE_STATUS_BADGE_VARIANT[status]}>
            {t(`pages.detail.status.${status}`)}
          </Badge>
          <span className="text-sm text-muted-foreground hidden lg:inline">
            {t('pages.detail.lastEdited', { date: lastEditedAt ?? '' })}
          </span>
        </>
      }
      actions={
        <>
          {planner.metadata.contentVersion < config.mdCurrentVersion && (
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
              disabled={publishMutation.isPending || isUploadingForPublish}
            >
              <Upload className="size-4" />
              <span className="hidden lg:inline">
                {t(
                  PUBLISH_LABEL_KEYS[publishMutation.isPending ? 'pending' : 'idle'][
                    planner.metadata.published ? 'unpublish' : 'publish'
                  ],
                )}
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
        </>
      }
    >
      {/* Delete Confirmation Dialog */}
      <DeleteConfirmDialog
        open={showDeleteDialog}
        onOpenChange={setShowDeleteDialog}
        plannerId={planner.metadata.id}
        plannerTitle={planner.metadata.title || t('untitled')}
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

      {/* Publish Sync-Off Warning Dialog */}
      <SyncOffWarningDialog
        action="publish"
        open={showPublishWarning}
        onOpenChange={setShowPublishWarning}
        onConfirm={handlePublishWithUpload}
        isPending={isUploadingForPublish || publishMutation.isPending}
      />
    </PlannerHeaderChrome>
  )
}
