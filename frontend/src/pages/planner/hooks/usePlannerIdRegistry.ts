import { useSuspenseQueries } from '@tanstack/react-query'
import { PLANNER_CONFIG } from '@/lib/constants'
import { useEGOListSpec } from '@/pages/ego'
import { useIdentityListSpec } from '@/pages/identity'
import { useThemePackListSpec } from '@/pages/themePack'
import type { StartBuffDataList } from '@/shared/gameText'
import { startBuffSpecQueryOptions } from './useStartBuffList'
import { startGiftPoolsQueryOptions } from './useStartGiftPools'
import { plannerIdRegistry } from '../lib/plannerIdRegistry'
import type { PlannerIdRegistry } from '../lib/validation/plannerValidation'
import type { StartEgoGiftPools } from '../types/StartGiftTypes'

export function usePlannerIdRegistry(): (contentVersion: number) => PlannerIdRegistry {
  const identitySpec = useIdentityListSpec()
  const egoSpec = useEGOListSpec()
  const themePackSpec = useThemePackListSpec()
  const versions = PLANNER_CONFIG.mdAvailableVersions
  const seasonData = useSuspenseQueries({
    queries: [
      ...versions.map((version) => startBuffSpecQueryOptions(version)),
      ...versions.map((version) => startGiftPoolsQueryOptions(version)),
    ],
  })

  return plannerIdRegistry({
    identitySpec,
    egoSpec,
    themePackSpec,
    seasons: versions.map((version, i) => ({
      version,
      startBuffs: (seasonData[i]?.data ?? {}) as StartBuffDataList,
      startGiftPools: (seasonData[versions.length + i]?.data ?? {}) as StartEgoGiftPools,
    })),
  })
}
