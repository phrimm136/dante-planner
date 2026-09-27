import { ApiClient } from '@/lib/api'

export function fetchPublishedPlannerRaw(
  plannerId: string,
  signal?: AbortSignal,
): Promise<unknown> {
  return ApiClient.get(`/api/planner/md/published/${plannerId}`, {
    ...(signal !== undefined && { signal }),
  })
}
