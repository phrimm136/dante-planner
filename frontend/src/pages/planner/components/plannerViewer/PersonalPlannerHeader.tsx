import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronsRight, Edit, Trash2, Upload } from 'lucide-react'

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
import { usePlannerSyncAdapter, acknowledgedCopy } from '../../hooks/usePlannerSyncAdapter'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/pages/egoGift'
import { plannerQueryKeys } from '../../lib/plannerQueryKeys'
import { deriveSaveStatus, SAVE_STATUS_BADGE_VARIANT } from '../../lib/plannerBadges'
import { decidePublishAction } from '../../lib/plannerPublishPolicy'
import { showAppError, showErrorMessage, showSuccess } from '@/lib/errorPresentation'
import { showSyncFailure } from '../../lib/syncFailure'
import { validatePlannerForPublish } from '../../lib/plannerValidation'
import { toUserFriendlyError } from '../../lib/plannerValidationErrors'

import { isMDPlanner } from '../../types/PlannerTypes'
import type { SaveablePlanner } from '../../types/PlannerTypes'

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
  planner: SaveablePlanner
  isAuthenticated: boolean
  syncEnabled?: boolean | null | undefined
  onEdit?: (() => void) | undefined
  onDelete?: (() => void) | undefined
}

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
  const egoGiftSpec = useEGOGiftListSpec()
  const egoGiftI18n = useEGOGiftListI18n()

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
    ...(syncEnabled !== undefined && { syncEnabled }),
    ...(onDelete !== undefined && { onDelete }),
  })

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
          const saveResult = await saveToLocal(updatedPlanner)

          void queryClient.invalidateQueries({
            queryKey: plannerQueryKeys.detail(plannerId),
          })

          setIsUploadingForPublish(false)

          if (!saveResult.ok) {
            showAppError(saveResult.error)
            return
          }

          showSuccess(
            wasPublished
              ? 'planner:pages.plannerMD.publish.unpublishSuccess'
              : 'planner:pages.plannerMD.publish.success',
          )
        },
        onError: () => {
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
      const synced = await syncAdapter.syncToServer(planner)
      callPublishMutation(false, acknowledgedCopy(synced))
    } catch (error) {
      console.error('Failed to upload plan for publishing:', error)
      showSyncFailure(error)
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
        showErrorMessage(`planner:${friendly.key}`, friendly.params)
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
              aria-label={t(
                PUBLISH_LABEL_KEYS[publishMutation.isPending ? 'pending' : 'idle'][
                  planner.metadata.published ? 'unpublish' : 'publish'
                ],
              )}
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
      <DeleteConfirmDialog
        open={showDeleteDialog}
        onOpenChange={setShowDeleteDialog}
        plannerId={planner.metadata.id}
        plannerTitle={planner.metadata.title || t('untitled')}
        onConfirm={handleDeleteConfirm}
        isPending={isDeletePending}
      />

      <ApplyLatestMirrorDialog
        open={showApplyLatestMirrorDialog}
        onOpenChange={setShowApplyLatestMirrorDialog}
        onConfirm={() => {
          void handleApplyLatestMirror()
        }}
        isPending={isApplyingLatestMirror}
      />

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
