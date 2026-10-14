import { queryClient } from '@/lib/queryClient'
import { PLANNER_CONFIG } from '@/lib/constants'
import { mirrorDungeonQueryOptions } from './useMirrorDungeonSeason'
import type { IdentityLevelCaps } from '../lib/plannerIdNormalize'

export async function loadIdentityMaxLevel(version: number): Promise<number> {
  const season = await queryClient.ensureQueryData(mirrorDungeonQueryOptions(version))
  return season.identityMaxLevel
}

export async function loadIdentityLevelCaps(): Promise<IdentityLevelCaps> {
  const seasons = await Promise.all(
    PLANNER_CONFIG.mdAvailableVersions.map((version) =>
      queryClient.ensureQueryData(mirrorDungeonQueryOptions(version)),
    ),
  )
  return new Map(seasons.map((season) => [season.version, season.identityMaxLevel]))
}
