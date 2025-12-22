import { useQuery, queryOptions } from '@tanstack/react-query'
import queryConfig from '@static/config/queryConfig.json'
import type { Affinity } from '@/lib/constants'

// Spec data types matching the JSON structure
export interface IdentitySpec {
  updateDate: number
  skillKeywordList?: string[]
  season: number
  rank: number
  unitKeywordList?: string[]
  associationList?: string[]
  attributeType: Affinity[]
  atkType: ('SLASH' | 'PENETRATE' | 'HIT')[]
}

export interface EGOSpec {
  updateDate: number
  egoType: 'ZAYIN' | 'TETH' | 'HE' | 'WAW' | 'ALEPH'
  season: number
  requirements: Partial<Record<Affinity, number>>
  attributeType: Affinity[]
  atkType: ('SLASH' | 'PENETRATE' | 'HIT')[]
}

// Query key factory for spec data
export const specQueryKeys = {
  identitySpec: () => ['spec', 'identity'] as const,
  egoSpec: () => ['spec', 'ego'] as const,
}

// Identity spec list query options
function createIdentitySpecQueryOptions() {
  return queryOptions({
    queryKey: specQueryKeys.identitySpec(),
    queryFn: async () => {
      const module = await import('@static/data/identitySpecList.json')
      return module.default as Record<string, IdentitySpec>
    },
    staleTime: queryConfig.staleTime.identity,
  })
}

// EGO spec list query options
function createEGOSpecQueryOptions() {
  return queryOptions({
    queryKey: specQueryKeys.egoSpec(),
    queryFn: async () => {
      const module = await import('@static/data/egoSpecList.json')
      return module.default as Record<string, EGOSpec>
    },
    staleTime: queryConfig.staleTime.ego,
  })
}

/**
 * Hook to load identity spec list data
 * @returns Identity spec map keyed by ID
 */
export function useIdentitySpecData() {
  return useQuery(createIdentitySpecQueryOptions())
}

/**
 * Hook to load EGO spec list data
 * @returns EGO spec map keyed by ID
 */
export function useEGOSpecData() {
  return useQuery(createEGOSpecQueryOptions())
}
