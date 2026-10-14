import { useSuspenseQuery } from '@tanstack/react-query'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import type { MDVersion } from '@/shared/gameData'
import { mirrorDungeonSeasonSchema } from '../schemas/MirrorDungeonSchemas'

export const mirrorDungeonQueryKeys = {
  all: (version: MDVersion) => ['mirrorDungeon', `md${version}`] as const,
}

export function mirrorDungeonQueryOptions(version: MDVersion) {
  return createStaticDataQueryOptions(
    mirrorDungeonQueryKeys.all(version),
    () => import(`@static/data/MD${version}/mirrorDungeon.json`),
    mirrorDungeonSeasonSchema(version),
    `mirrorDungeon/md${version}`,
  )
}

export function useIdentityMaxLevel(version: MDVersion): number {
  const { data } = useSuspenseQuery(mirrorDungeonQueryOptions(version))
  return data.identityMaxLevel
}
