import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'

import { INITIAL_SYNC_VERSION } from '@/lib/constants'
import { showAppError, showSuccess } from '@/lib/errorPresentation'
import { showSyncFailure } from '../lib/syncFailure'
import { plannerQueryKeys } from '../lib/plannerQueryKeys'
import { publishedPlannerQueryKeys } from './usePublishedPlannerQuery'
import { useInvalidatePlannerLists } from './useInvalidatePlannerLists'
import { usePlannerDelete } from './usePlannerDelete'
import { usePlannerStorage } from './usePlannerStorage'
import { usePlannerSyncAdapter, acknowledgedCopy } from './usePlannerSyncAdapter'
import { usePlannerConfig } from './usePlannerConfig'

import type { SaveablePlanner } from '../types/PlannerTypes'

const NAVIGATE_AFTER_DELETE_MS = 150

interface UsePlannerHeaderActionsOptions {
  plannerId: string | undefined
  listRoute: '/planner/md' | '/planner/md/gesellschaft'
  plannerToUpdate: SaveablePlanner | undefined
  isAuthenticated: boolean
  syncEnabled?: boolean | null
  onDelete?: () => void
}

export function usePlannerHeaderActions({
  plannerId,
  listRoute,
  plannerToUpdate,
  isAuthenticated,
  syncEnabled,
  onDelete,
}: UsePlannerHeaderActionsOptions) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const invalidatePlannerLists = useInvalidatePlannerLists()
  const config = usePlannerConfig()

  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [showApplyLatestMirrorDialog, setShowApplyLatestMirrorDialog] = useState(false)
  const [isApplyingLatestMirror, setIsApplyingLatestMirror] = useState(false)

  const deleteMutation = usePlannerDelete()
  const { saveToLocal, deleteFromLocal, loadFromLocal, writeTombstone } = usePlannerStorage()
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
      invalidatePlannerLists()
      setShowDeleteDialog(false)
      setTimeout(() => {
        void navigate({ to: listRoute })
      }, NAVIGATE_AFTER_DELETE_MS)
    }

    const tombstoneThenCleanup = async () => {
      const local = await loadFromLocal(plannerId)
      const written = await writeTombstone({
        id: plannerId,
        syncVersion:
          local.ok && local.value ? local.value.metadata.syncVersion : INITIAL_SYNC_VERSION,
        deletedAt: new Date().toISOString(),
      })
      if (written.ok) cleanup()
      else showAppError(written.error)
    }

    if (!isAuthenticated) {
      void tombstoneThenCleanup()
      return
    }

    deleteMutation.mutate(plannerId, {
      onSuccess: (outcome) => {
        if (outcome === 'unauthorized') void tombstoneThenCleanup()
        else cleanup()
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
        const synced = await syncAdapter.syncToServer(updatedPlanner)

        const localRead = await loadFromLocal(plannerId)
        const localDraft =
          localRead.ok && localRead.value?.metadata.status === 'draft' ? localRead.value : null
        if (localDraft) {
          await saveToLocal({
            ...localDraft,
            metadata: {
              ...localDraft.metadata,
              contentVersion: config.mdCurrentVersion,
              syncVersion: Math.max(localDraft.metadata.syncVersion ?? 0, synced.ack.syncVersion),
            },
          })
        } else {
          await saveToLocal(acknowledgedCopy(synced))
        }
      } else {
        await saveToLocal(updatedPlanner)
      }

      void queryClient.invalidateQueries({ queryKey: plannerQueryKeys.detail(plannerId) })
      void queryClient.invalidateQueries({ queryKey: publishedPlannerQueryKeys.detail(plannerId) })

      showSuccess('planner:pages.plannerMD.applyLatestMirror.success')
    }

    await applyUpdate()
      .catch((error: unknown) => {
        console.error('Failed to apply latest mirror:', error)
        showSyncFailure(error)
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
