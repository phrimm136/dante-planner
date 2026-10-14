import i18n from '@/lib/i18n'
import { queryClient } from '@/lib/queryClient'
import { PLANNER_CONFIG } from '@/lib/constants'
import { entityListI18nOptions, entityListSpecOptions } from '@/shared/entityCatalog'
import { EGO_LIST } from '@/pages/ego'
import { EGO_GIFT_LIST } from '@/pages/egoGift'
import { IDENTITY_LIST } from '@/pages/identity'
import { THEME_PACK_LIST } from '@/pages/themePack'
import { startBuffSpecQueryOptions } from './useStartBuffList'
import { startGiftPoolsQueryOptions } from './useStartGiftPools'
import { mirrorDungeonQueryOptions } from './useMirrorDungeonSeason'
import { plannerIdRegistry } from '../lib/plannerIdRegistry'
import type { EGOGiftSpec } from '@/pages/egoGift'
import type { PlannerIdRegistry } from '../lib/validation/plannerValidation'

export type ValidationInputs = {
  egoGiftSpec: Record<string, EGOGiftSpec>
  egoGiftI18n: Record<string, string>
  registry: PlannerIdRegistry
}

export async function loadValidationInputs(contentVersion: number): Promise<ValidationInputs> {
  const versions = PLANNER_CONFIG.mdAvailableVersions
  const [
    egoGiftSpec,
    egoGiftI18n,
    identitySpec,
    egoSpec,
    themePackSpec,
    startBuffs,
    startGiftPools,
    mirrorDungeons,
  ] = await Promise.all([
    queryClient.ensureQueryData(entityListSpecOptions(EGO_GIFT_LIST)),
    queryClient.ensureQueryData(entityListI18nOptions(EGO_GIFT_LIST, i18n.language)),
    queryClient.ensureQueryData(entityListSpecOptions(IDENTITY_LIST)),
    queryClient.ensureQueryData(entityListSpecOptions(EGO_LIST)),
    queryClient.ensureQueryData(entityListSpecOptions(THEME_PACK_LIST)),
    Promise.all(
      versions.map((version) => queryClient.ensureQueryData(startBuffSpecQueryOptions(version))),
    ),
    Promise.all(
      versions.map((version) => queryClient.ensureQueryData(startGiftPoolsQueryOptions(version))),
    ),
    Promise.all(
      versions.map((version) => queryClient.ensureQueryData(mirrorDungeonQueryOptions(version))),
    ),
  ])
  const registryFor = plannerIdRegistry({
    identitySpec,
    egoSpec,
    themePackSpec,
    seasons: mirrorDungeons.map(({ version, identityMaxLevel }, i) => ({
      version,
      startBuffs: startBuffs[i] ?? {},
      startGiftPools: startGiftPools[i] ?? {},
      identityMaxLevel,
    })),
  })
  return { egoGiftSpec, egoGiftI18n, registry: registryFor(contentVersion) }
}
