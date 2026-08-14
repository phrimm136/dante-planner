import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { toast } from '@/lib/toast'
import { NotFoundError } from '@/lib/api'
import { toastForError } from '../lib/plannerSaveErrors'
import { plannerQueryKeys } from '../lib/plannerQueryKeys'
import { publishedPlannerQueryKeys } from './usePublishedPlannerQuery'
import { userPlannersQueryKeys } from './useMDUserPlannersData'
import { usePlannerDelete } from './usePlannerDelete'
import { usePlannerStorage } from './usePlannerStorage'
import { usePlannerSyncAdapter, acknowledgedCopy } from './usePlannerSyncAdapter'
import { usePlannerConfig } from './usePlannerConfig'

import type { SaveablePlanner } from '../types/PlannerTypes'

/** Delay before leaving, so the dialog's close animation is not cut off. */
const NAVIGATE_AFTER_DELETE_MS = 150

interface UsePlannerHeaderActionsOptions {
  plannerId: string | undefined
  /** List route this header navigates back to, and lands on after a delete. */
  listRoute: '/planner/md' | '/planner/md/gesellschaft'
  /** Planner the "apply latest mirror" upgrade rewrites, when one is available. */
  plannerToUpdate: SaveablePlanner | undefined
  isAuthenticated: boolean
  syncEnabled?: boolean | null
  /** Overrides the built-in delete when the page owns deletion. */
  onDelete?: () => void
}

/**
 * Back navigation, deletion and the content-version upgrade — the actions both
 * planner detail headers offer, with the dialog state they drive.
 */
export function usePlannerHeaderActions({
  plannerId,
  listRoute,
  plannerToUpdate,
  isAuthenticated,
  syncEnabled,
  onDelete,
}: UsePlannerHeaderActionsOptions) {
  const { t } = useTranslation(['planner', 'common'])
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const config = usePlannerConfig()

  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [showApplyLatestMirrorDialog, setShowApplyLatestMirrorDialog] = useState(false)
  const [isApplyingLatestMirror, setIsApplyingLatestMirror] = useState(false)

  const deleteMutation = usePlannerDelete()
  const { saveToLocal, deleteFromLocal } = usePlannerStorage()
  const syncAdapter = usePlannerSyncAdapter()

  const handleBack = () => {
    void navigate({ to: listRoute })
  }

  const handleDeleteConfirm = () => {
    if (onDelete) {
      onDelete()
      return
    }
    if (!plannerId) return

    const cleanup = () => {
      void deleteFromLocal(plannerId)
      void queryClient.invalidateQueries({ queryKey: userPlannersQueryKeys.all })
      setShowDeleteDialog(false)
      setTimeout(() => {
        void navigate({ to: listRoute })
      }, NAVIGATE_AFTER_DELETE_MS)
    }

    deleteMutation.mutate(plannerId, {
      onSuccess: cleanup,
      onError: (error) => {
        // Planner not on server (local-only or already deleted) — still clean up locally
        if (error instanceof NotFoundError) cleanup()
      },
    })
  }

  const handleApplyLatestMirror = async () => {
    if (!plannerToUpdate || !plannerId) return

    setIsApplyingLatestMirror(true)
    setShowApplyLatestMirrorDialog(false)

    const applyUpdate = async () => {
      const updatedPlanner: SaveablePlanner = {
        ...plannerToUpdate,
        metadata: {
          ...plannerToUpdate.metadata,
          contentVersion: config.mdCurrentVersion,
        },
      }

      if (isAuthenticated && (syncEnabled === true || !!plannerToUpdate.metadata.published)) {
        // The server's copy is persisted under the version its ack assigned;
        // keeping the pre-sync version would make every later non-forced
        // upload conflict (409).
        const synced = await syncAdapter.syncToServer(updatedPlanner)
        await saveToLocal(acknowledgedCopy(synced))
      } else {
        // Local-only save (personal, sync disabled / unauthenticated)
        // syncVersion unchanged — server-assigned, must not drift without server confirmation
        await saveToLocal(updatedPlanner)
      }

      void queryClient.invalidateQueries({ queryKey: plannerQueryKeys.detail(plannerId) })
      void queryClient.invalidateQueries({ queryKey: publishedPlannerQueryKeys.detail(plannerId) })

      toast.success(t('pages.plannerMD.applyLatestMirror.success'))
    }

    await applyUpdate()
      .catch((error: unknown) => {
        console.error('Failed to apply latest mirror:', error)
        toastForError(error, 'pages.plannerMD.applyLatestMirror.failed')
      })
      .finally(() => {
        setIsApplyingLatestMirror(false)
      })
  }

  return {
    config,
    handleBack,
    showDeleteDialog,
    setShowDeleteDialog,
    handleDeleteConfirm,
    isDeletePending: deleteMutation.isPending,
    showApplyLatestMirrorDialog,
    setShowApplyLatestMirrorDialog,
    isApplyingLatestMirror,
    handleApplyLatestMirror,
  }
}
