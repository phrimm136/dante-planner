import { useSuspenseQueries } from '@tanstack/react-query'
import { PLANNER_CONFIG } from '@/lib/constants'
import { useEGOListSpec } from '@/pages/ego'
import { useIdentityListSpec } from '@/pages/identity'
import { useThemePackListSpec } from '@/pages/themePack'
import type { StartBuffDataList } from '@/shared/gameText'
import { startBuffSpecQueryOptions } from './useStartBuffList'
import { startGiftPoolsQueryOptions } from './useStartGiftPools'
import type { PlannerIdRegistry } from '../lib/validation/plannerValidation'
import type { StartEgoGiftPools } from '../types/StartGiftTypes'

type Season = NonNullable<PlannerIdRegistry['season']>

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

  const identityIds = new Set<string>(Object.keys(identitySpec))
  const egoIds = new Set<string>(Object.keys(egoSpec))
  const egoMaxThreadspin = new Map<string, number>(
    Object.entries(egoSpec).map(([egoId, spec]) => [egoId, spec.maxThreadspin]),
  )
  const themePackIds = new Set<string>(Object.keys(themePackSpec))
  const startBuffs = seasonData
    .slice(0, versions.length)
    .map(({ data }) => data as StartBuffDataList)
  const startGiftPools = seasonData
    .slice(versions.length)
    .map(({ data }) => data as StartEgoGiftPools)
  const seasons = new Map<number, Season>(
    versions.map((version, i) => [
      version,
      {
        startBuffIds: new Set(Object.keys(startBuffs[i] ?? {})),
        startGiftPools: new Map(
          Object.entries(startGiftPools[i] ?? {}).map(([keyword, giftIds]) => [
            keyword,
            new Set<string>(giftIds),
          ]),
        ),
      },
    ]),
  )

  return (contentVersion) => ({
    identityIds,
    egoIds,
    egoMaxThreadspin,
    themePackIds,
    season: seasons.get(contentVersion),
  })
}
