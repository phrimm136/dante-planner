import type { StartBuffDataList } from '@/shared/gameText'
import type { PlannerIdRegistry } from './validation/plannerValidation'
import type { StartEgoGiftPools } from '../types/StartGiftTypes'

type Season = NonNullable<PlannerIdRegistry['season']>

export type SeasonSource = {
  version: number
  startBuffs: StartBuffDataList
  startGiftPools: StartEgoGiftPools
  identityMaxLevel: number
}

export type PlannerIdRegistrySources = {
  identitySpec: Record<string, unknown>
  egoSpec: Record<string, { maxThreadspin: number }>
  themePackSpec: Record<string, unknown>
  seasons: readonly SeasonSource[]
}

const seasonOf = ({ startBuffs, startGiftPools, identityMaxLevel }: SeasonSource): Season => ({
  startBuffIds: new Set(Object.keys(startBuffs)),
  identityMaxLevel,
  startGiftPools: new Map(
    Object.entries(startGiftPools).map(([keyword, giftIds]) => [keyword, new Set<string>(giftIds)]),
  ),
})

export function plannerIdRegistry(
  sources: PlannerIdRegistrySources,
): (contentVersion: number) => PlannerIdRegistry {
  const identityIds = new Set<string>(Object.keys(sources.identitySpec))
  const egoIds = new Set<string>(Object.keys(sources.egoSpec))
  const egoMaxThreadspin = new Map<string, number>(
    Object.entries(sources.egoSpec).map(([egoId, spec]) => [egoId, spec.maxThreadspin]),
  )
  const themePackIds = new Set<string>(Object.keys(sources.themePackSpec))
  const seasons = new Map<number, Season>(
    sources.seasons.map((source) => [source.version, seasonOf(source)]),
  )

  return (contentVersion) => ({
    identityIds,
    egoIds,
    egoMaxThreadspin,
    themePackIds,
    season: seasons.get(contentVersion),
  })
}
