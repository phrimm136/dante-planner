import { useQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { StartBuffDataList, StartBuffI18n, StartBuff } from '@/types/StartBuffTypes'
import { BASE_BUFF_IDS } from '@/types/StartBuffTypes'

/**
 * Mirror Dungeon version type
 */
export type MDVersion = 5 | 6

// Query key factory for start buff data
export const startBuffQueryKeys = {
  all: (version: MDVersion) => ['startBuff', `md${version}`] as const,
  data: (version: MDVersion) => [...startBuffQueryKeys.all(version), 'data'] as const,
  i18n: (version: MDVersion, language: string) => [...startBuffQueryKeys.all(version), 'i18n', language] as const,
}

// Data query options
function createDataQueryOptions(version: MDVersion) {
  return queryOptions({
    queryKey: startBuffQueryKeys.data(version),
    queryFn: async () => {
      const module = await import(`@static/data/MD${version}/startBuffs.json`)
      return module.default as StartBuffDataList
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

// I18n query options
function createI18nQueryOptions(version: MDVersion, language: string) {
  return queryOptions({
    queryKey: startBuffQueryKeys.i18n(version, language),
    queryFn: async () => {
      const module = await import(`@static/i18n/${language}/MD${version}/startBuffs.json`)
      return module.default as StartBuffI18n
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads start buff data with i18n translations
 * @param version - Mirror Dungeon version (5 or 6)
 * Returns all buffs for the specified version with merged translations
 */
export function useStartBuffData(version: MDVersion) {
  const { i18n } = useTranslation()

  const dataQuery = useQuery(createDataQueryOptions(version))
  const i18nQuery = useQuery(createI18nQueryOptions(version, i18n.language))

  // Merge data with i18n
  const mergedData = dataQuery.data && i18nQuery.data
    ? Object.entries(dataQuery.data).map(([id, buff]) => ({
        id,
        baseId: buff.baseId,
        level: buff.level,
        name: i18nQuery.data[buff.localizeId] || buff.localizeId,
        cost: buff.cost,
        effects: buff.effects,
        iconSpriteId: buff.uiConfig.iconSpriteId,
      } as StartBuff))
    : undefined

  return {
    data: mergedData,
    i18n: i18nQuery.data,
    isPending: dataQuery.isPending || i18nQuery.isPending,
    isError: dataQuery.isError || i18nQuery.isError,
    error: dataQuery.error || i18nQuery.error,
  }
}

/**
 * Gets a specific buff by ID from the data
 */
export function getBuffById(buffs: StartBuff[] | undefined, id: number): StartBuff | undefined {
  return buffs?.find(b => b.id === String(id))
}

/**
 * Gets all buffs for a specific base ID (all enhancement levels)
 */
export function getBuffsByBaseId(buffs: StartBuff[] | undefined, baseId: number): StartBuff[] {
  return buffs?.filter(b => b.baseId === baseId) ?? []
}

/**
 * Gets the 10 base buffs (level 1) for initial display
 */
export function getBaseBuffs(buffs: StartBuff[] | undefined): StartBuff[] {
  return buffs?.filter(b => BASE_BUFF_IDS.includes(b.baseId as typeof BASE_BUFF_IDS[number]) && b.level === 1) ?? []
}
