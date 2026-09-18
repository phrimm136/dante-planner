import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { INITIAL_SYNC_VERSION } from '@/lib/constants'

import { useInvalidatePlannerLists } from './useInvalidatePlannerLists'
import { usePlannerStorage } from './usePlannerStorage'
import { usePlannerSyncAdapter } from './usePlannerSyncAdapter'
import { useUserSettingsQuery } from '@/shared/userSettings'
import { useAuthQuery } from '@/shared/auth'
import { ApiClient } from '@/lib/api'
import { generateUUID } from '@/lib/uuid'
import { validateData } from '@/lib/validation'
import { PublishedPlannerDetailSchema } from '../schemas/PlannerListSchemas'
import { toSaveablePlanner, PlannerConfigDiscriminatedSchema } from '../schemas/PlannerSchemas'

import type { PublishedPlannerDetail } from '../types/PlannerListTypes'

interface ForkInput {
  plannerId: string
  planner?: PublishedPlannerDetail
}

interface ForkResult {
  newPlannerId: string
}

export function usePlannerFork() {
  const invalidatePlannerLists = useInvalidatePlannerLists()
  const storage = usePlannerStorage()
  const syncAdapter = usePlannerSyncAdapter()
  const { t } = useTranslation('planner')
  const { data: user } = useAuthQuery()
  const { data: settings } = useUserSettingsQuery()
  const isAuthenticated = !!user
  const syncEnabled = settings?.syncEnabled === true

  return useMutation({
    mutationFn: async ({ plannerId, planner }: ForkInput): Promise<ForkResult> => {
      let plannerData = planner
      if (!plannerData) {
        const data = await ApiClient.get(`/api/planner/md/published/${plannerId}`)
        plannerData = validateData(
          data,
          PublishedPlannerDetailSchema,
          `planner fork source / ${plannerId}`,
        )
      }

      const newPlannerId = generateUUID()

      const baseTitle = plannerData.title
      const copyTitle = t('pages.plannerMD.conflict.copySuffix', '{{title}} (Copy)', {
        title: baseTitle,
      })

      const contentData = JSON.parse(plannerData.content)

      const now = new Date().toISOString()
      const newPlanner = toSaveablePlanner(
        {
          id: newPlannerId,
          title: copyTitle,
          status: 'saved', // Mark as saved (immediately persisted to local)
          schemaVersion: plannerData.schemaVersion,
          contentVersion: plannerData.contentVersion,
          plannerType: plannerData.plannerType,
          syncVersion: INITIAL_SYNC_VERSION,
          createdAt: now,
          lastModifiedAt: now,
          published: false, // New copy is not published
        },
        PlannerConfigDiscriminatedSchema.parse({
          type: plannerData.plannerType,
          category: plannerData.category,
        }),
        contentData, // Parsed content object
      )

      await storage.saveToLocal(newPlanner)

      if (isAuthenticated && syncEnabled) {
        try {
          await syncAdapter.syncToServer(newPlanner)
        } catch (syncError) {
          console.warn('Failed to sync forked planner to server:', syncError)
        }
      }

      return { newPlannerId }
    },
    onSuccess: () => {
      invalidatePlannerLists()
    },
    onError: (error) => {
      console.error('Fork failed:', error)
    },
  })
}
