import { useSuspenseQuery } from '@tanstack/react-query'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import type { MDVersion } from '@/shared/gameData'
import {
  StartBuffCardLayoutSchema,
  StartBuffMiniCardLayoutSchema,
  type StartBuffCardLayout,
  type StartBuffMiniCardLayout,
} from '../schemas/StartBuffCardSchemas'
import { startBuffQueryKeys } from './useStartBuffList'

const startBuffLayoutQueryKeys = {
  card: (version: MDVersion) => [...startBuffQueryKeys.all(version), 'cardLayout'] as const,
  miniCard: (version: MDVersion) => [...startBuffQueryKeys.all(version), 'miniCardLayout'] as const,
}

export function startBuffCardLayoutQueryOptions(version: MDVersion) {
  return createStaticDataQueryOptions(
    startBuffLayoutQueryKeys.card(version),
    () => import(`@static/data/MD${version}/startBuffCard.json`),
    StartBuffCardLayoutSchema,
    `startBuffCard/md${version}`,
  )
}

export function startBuffMiniCardLayoutQueryOptions(version: MDVersion) {
  return createStaticDataQueryOptions(
    startBuffLayoutQueryKeys.miniCard(version),
    () => import(`@static/data/MD${version}/startBuffMiniCard.json`),
    StartBuffMiniCardLayoutSchema,
    `startBuffMiniCard/md${version}`,
  )
}

export function useStartBuffCardLayout(version: MDVersion): StartBuffCardLayout {
  const { data } = useSuspenseQuery(startBuffCardLayoutQueryOptions(version))
  return data
}

export function useStartBuffMiniCardLayout(version: MDVersion): StartBuffMiniCardLayout {
  const { data } = useSuspenseQuery(startBuffMiniCardLayoutQueryOptions(version))
  return data
}
