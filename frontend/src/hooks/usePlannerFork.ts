/**
 * Planner Fork Mutation Hook
 *
 * Handles forking (copying) a published planner.
 * Creates a local copy with optional server sync (similar to conflict resolution).
 * Works for both authenticated and unauthenticated users.
 *
 * Pattern: Conflict resolution "Save as Copy" (usePlannerSave.ts)
 */

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { gesellschaftQueryKeys } from './useMDGesellschaftData'
import { usePlannerSaveAdapter } from './usePlannerSaveAdapter'
import { usePlannerSyncAdapter } from './usePlannerSyncAdapter'
import { useUserSettingsQuery } from './useUserSettings'
import { useAuthQuery } from './useAuthQuery'
import { ApiClient } from '@/lib/api'
import { PublishedPlannerDetailSchema } from '@/schemas/PlannerListSchemas'

import type { PublishedPlannerDetail } from '@/types/PlannerListTypes'
import type { SaveablePlanner, PlannerConfig } from '@/types/PlannerTypes'

// ============================================================================
// Types
// ============================================================================

interface ForkInput {
  /** Planner ID to fork (required) */
  plannerId: string
  /** Published planner data (optional - will fetch if not provided) */
  planner?: PublishedPlannerDetail
}

interface ForkResult {
  /** ID of the newly created planner copy */
  newPlannerId: string
}

// ============================================================================
// Helper
// ============================================================================

/**
 * Generate UUID v4
 */
function generateUUID(): string {
  return crypto.randomUUID()
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook for forking (copying) published planners
 *
 * @example
 * ```tsx
 * function PlannerDetailFooter({ planner }) {
 *   const fork = usePlannerFork();
 *   const navigate = useNavigate();
 *
 *   const handleFork = () => {
 *     fork.mutate({ planner }, {
 *       onSuccess: (result) => {
 *         navigate({ to: '/planner/md/$id/edit', params: { id: result.newPlannerId } });
 *       },
 *     });
 *   };
 *
 *   return (
 *     <button onClick={handleFork} disabled={fork.isPending}>
 *       Copy
 *     </button>
 *   );
 * }
 * ```
 */
export function usePlannerFork() {
  const queryClient = useQueryClient()
  const saveAdapter = usePlannerSaveAdapter()
  const syncAdapter = usePlannerSyncAdapter()
  const { t } = useTranslation('planner')
  const { data: user } = useAuthQuery()
  const { data: settings } = useUserSettingsQuery()
  const isAuthenticated = !!user
  const syncEnabled = settings?.syncEnabled === true

  return useMutation({
    mutationFn: async ({ plannerId, planner }: ForkInput): Promise<ForkResult> => {
      // 0. Fetch planner if not provided (context menu case)
      let plannerData = planner
      if (!plannerData) {
        const data = await ApiClient.get(`/api/planner/md/published/${plannerId}`)
        const result = PublishedPlannerDetailSchema.safeParse(data)

        if (!result.success) {
          console.error('Failed to fetch planner for fork:', result.error)
          throw new Error('Invalid planner data from server')
        }

        plannerData = result.data
      }

      // 1. Generate new planner ID and get device ID
      const newPlannerId = generateUUID()
      const deviceId = await saveAdapter.getOrCreateDeviceId()

      if (!deviceId) {
        throw new Error('Failed to get device ID')
      }

      // 2. Apply i18n copySuffix to title
      const baseTitle = plannerData.title
      const copyTitle = t('pages.plannerMD.conflict.copySuffix', '{{title}} (Copy)', {
        title: baseTitle,
      })

      // 3. Parse content from JSON string
      const contentData = JSON.parse(plannerData.content)

      // 4. Create new SaveablePlanner with correct metadata structure
      const now = new Date().toISOString()
      const newPlanner: SaveablePlanner = {
        metadata: {
          id: newPlannerId,
          title: copyTitle,
          status: 'saved', // Mark as saved (immediately persisted to local)
          schemaVersion: plannerData.schemaVersion,
          contentVersion: plannerData.contentVersion,
          plannerType: plannerData.plannerType,
          syncVersion: 1, // Initial version for new planner
          createdAt: now,
          lastModifiedAt: now,
          savedAt: now,
          deviceId,
          published: false, // New copy is not published
        },
        config: {
          type: plannerData.plannerType,
          category: plannerData.category,
        } as PlannerConfig,
        content: contentData, // Parsed content object
      }

      // 5. Save to local storage
      await saveAdapter.saveToLocal(newPlanner)

      // 6. Optionally sync to server if authenticated AND sync enabled
      // This follows the same pattern as conflict resolution's keepBoth
      if (isAuthenticated && syncEnabled) {
        try {
          await syncAdapter.syncToServer(newPlanner)
        } catch (syncError) {
          // Log but don't fail - user can sync later via normal save flow
          console.warn('Failed to sync forked planner to server:', syncError)
        }
      }

      return { newPlannerId }
    },
    onSuccess: () => {
      // Invalidate planner list queries (my plans will have new entry)
      void queryClient.invalidateQueries({ queryKey: gesellschaftQueryKeys.all })
    },
    onError: (error) => {
      console.error('Fork failed:', error)
    },
  })
}
