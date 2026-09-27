import { useSuspenseQueries } from '@tanstack/react-query'
import { PLANNER_CONFIG } from '@/lib/constants'
import { useEGOListSpec } from '@/pages/ego'
import { useIdentityListSpec } from '@/pages/identity'
import { useThemePackListSpec } from '@/pages/themePack'
import { startBuffSpecQueryOptions } from './useStartBuffList'
import type { PlannerIdRegistry } from '../lib/plannerValidation'

const NO_IDS: ReadonlySet<string> = new Set()

export function usePlannerIdRegistry(): (contentVersion: number) => PlannerIdRegistry {
  const identitySpec = useIdentityListSpec()
  const egoSpec = useEGOListSpec()
  const themePackSpec = useThemePackListSpec()
  const startBuffSpecs = useSuspenseQueries({
    queries: PLANNER_CONFIG.mdAvailableVersions.map((version) =>
      startBuffSpecQueryOptions(version),
    ),
  })

  const identityIds = new Set<string>(Object.keys(identitySpec))
  const egoIds = new Set<string>(Object.keys(egoSpec))
  const themePackIds = new Set<string>(Object.keys(themePackSpec))
  const startBuffIdsByVersion = new Map<number, ReadonlySet<string>>(
    PLANNER_CONFIG.mdAvailableVersions.map((version, i) => [
      version,
      new Set(Object.keys(startBuffSpecs[i]?.data ?? {})),
    ]),
  )

  return (contentVersion) => ({
    identityIds,
    egoIds,
    themePackIds,
    startBuffIds: startBuffIdsByVersion.get(contentVersion) ?? NO_IDS,
  })
}
