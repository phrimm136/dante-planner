import { useQuery } from '@tanstack/react-query'
import { useLocation } from '@tanstack/react-router'

import { PLANNER_PATH_PREFIX } from '@/lib/constants'
import { probeStorage } from '@/lib/storage'

const storageProbeQueryKeys = {
  probe: () => ['storageProbe'] as const,
}

export function useStorageUnavailable(): boolean {
  const onPlanner = useLocation({
    select: (location) => location.pathname.startsWith(PLANNER_PATH_PREFIX),
  })
  const { data } = useQuery({
    queryKey: storageProbeQueryKeys.probe(),
    queryFn: probeStorage,
    enabled: onPlanner,
    staleTime: Infinity,
    retry: false,
  })
  return onPlanner && data === false
}
