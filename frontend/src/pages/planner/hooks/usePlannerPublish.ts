import { useMutation } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { showError } from '@/lib/errorPresentation'
import { validateData } from '@/lib/validation'
import { requestNotificationPermission } from '@/shared/notifications'
import { ServerPlannerResponseSchema } from '../schemas/PlannerSchemas'
import { showSyncFailure } from '../lib/syncFailure'
import { useInvalidatePlannerLists } from './useInvalidatePlannerLists'
import { serverResponseToSaveable, toUpsertRequest } from './usePlannerSyncAdapter'

import type { SaveablePlanner } from '../types/PlannerTypes'

export type PublishVariables =
  | { intent: 'publish'; planner: SaveablePlanner }
  | { intent: 'unpublish'; plannerId: string }

export interface PublishOutcome {
  published: boolean
  acknowledged: SaveablePlanner | null
}

const PLANNERS_BASE = '/api/planner/md'

async function sendPublish(variables: PublishVariables): Promise<PublishOutcome> {
  if (variables.intent === 'unpublish') {
    const data = await ApiClient.post(`${PLANNERS_BASE}/${variables.plannerId}/unpublish`)
    const response = validateData(data, ServerPlannerResponseSchema, 'planner unpublish')
    return { published: response.published, acknowledged: null }
  }

  const { planner } = variables
  const data = await ApiClient.post(
    `${PLANNERS_BASE}/${planner.metadata.id}/publish`,
    toUpsertRequest(planner),
  )
  const response = validateData(data, ServerPlannerResponseSchema, 'planner publish')
  return { published: response.published, acknowledged: serverResponseToSaveable(response) }
}

export function usePlannerPublish() {
  const invalidatePlannerLists = useInvalidatePlannerLists()

  return useMutation({
    mutationFn: sendPublish,
    meta: { suppressErrorToast: true },
    onSuccess: (outcome) => {
      invalidatePlannerLists()

      if (outcome.published) {
        void requestNotificationPermission()
      }
    },
    onError: (error, variables) => {
      if (variables.intent === 'publish') {
        console.error('Failed to upload plan for publishing:', error)
        showSyncFailure(error)
        return
      }
      console.error('Publish toggle failed:', error)
      showError(error)
    },
  })
}
