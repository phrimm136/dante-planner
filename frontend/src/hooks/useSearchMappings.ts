import { useMemo } from 'react'
import { useSuspenseQuery, useQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { KeywordMatchSchema, UnitKeywordsSchema } from '@/schemas'
import type { KeywordMatch, UnitKeywords } from '@/schemas/SearchMappingSchemas'

// Query key factory for search mappings
export const searchMappingsQueryKeys = {
  all: ['searchMappings'] as const,
  keywordMatch: (language: string) => [...searchMappingsQueryKeys.all, 'keyword', language] as const,
  unitKeywords: (language: string) => [...searchMappingsQueryKeys.all, 'unit', language] as const,
}

// Keyword match query options with validation
function createKeywordMatchQueryOptions(language: string) {
  return queryOptions({
    queryKey: searchMappingsQueryKeys.keywordMatch(language),
    queryFn: async (): Promise<KeywordMatch> => {
      try {
        const response = await fetch(`/i18n/${language}/keywordMatch.json`)
        // File doesn't exist for this language - return empty object
        if (response.status === 404) {
          return {}
        }
        if (!response.ok) throw new Error(`Failed to fetch keywordMatch.json: ${response.status}`)
        const data: unknown = await response.json()
        const result = KeywordMatchSchema.safeParse(data)

        if (!result.success) {
          if (import.meta.env.DEV) {
            console.error('[keywordMatch] Validation failed:', result.error.issues)
          }
          throw new Error(`[keywordMatch / ${language}] Invalid data structure`)
        }

        return result.data
      } catch (error) {
        // Re-throw validation errors
        throw error
      }
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days - i18n data rarely changes
  })
}

// Unit keywords query options with validation
function createUnitKeywordsQueryOptions(language: string) {
  return queryOptions({
    queryKey: searchMappingsQueryKeys.unitKeywords(language),
    queryFn: async (): Promise<UnitKeywords> => {
      try {
        const response = await fetch(`/i18n/${language}/unitKeywords.json`)
        // File doesn't exist for this language - return empty object
        if (response.status === 404) {
          return {}
        }
        if (!response.ok) throw new Error(`Failed to fetch unitKeywords.json: ${response.status}`)
        const data: unknown = await response.json()
        const result = UnitKeywordsSchema.safeParse(data)

        if (!result.success) {
          if (import.meta.env.DEV) {
            console.error('[unitKeywords] Validation failed:', result.error.issues)
          }
          throw new Error(`[unitKeywords / ${language}] Invalid data structure`)
        }

        return result.data
      } catch (error) {
        // Re-throw validation errors
        throw error
      }
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days - i18n data rarely changes
  })
}

export interface SearchMappings {
  /** Maps display name (lowercase) -> internal code(s) for skill keywords */
  keywordToValue: Map<string, string[]>
  /** Maps display name (lowercase) -> internal code(s) for unit keywords/traits */
  unitKeywordToValue: Map<string, string[]>
}

/**
 * Hook that loads search mappings with React Query
 * Suspends while loading - wrap in Suspense boundary
 *
 * Creates reverse mappings for search:
 * - keywordToValue: "rupture" -> ["Burst"], "burn" -> ["Combustion"]
 * - unitKeywordToValue: "blade lineage" -> ["BLADE_LINEAGE"]
 *
 * These allow users to search using natural language terms and match
 * against internal game codes stored in entity data.
 */
export function useSearchMappings(): SearchMappings {
  const { i18n } = useTranslation()

  const { data: keywordMatch } = useSuspenseQuery(createKeywordMatchQueryOptions(i18n.language))
  const { data: unitKeywords } = useSuspenseQuery(createUnitKeywordsQueryOptions(i18n.language))

  // Build reverse mappings: display name (lowercase) -> internal code(s)
  return useMemo(() => {
    const keywordToValue = new Map<string, string[]>()
    const unitKeywordToValue = new Map<string, string[]>()

    // Build reverse map for skill keywords
    // keywordMatch: { "Burst": "Rupture" } -> keywordToValue: { "rupture": ["Burst"] }
    Object.entries(keywordMatch).forEach(([internalCode, displayName]) => {
      const lowerDisplay = displayName.toLowerCase()
      if (!keywordToValue.has(lowerDisplay)) {
        keywordToValue.set(lowerDisplay, [])
      }
      keywordToValue.get(lowerDisplay)!.push(internalCode)
    })

    // Build reverse map for unit keywords (traits, associations)
    // unitKeywords: { "BLADE_LINEAGE": "Blade Lineage" } -> unitKeywordToValue: { "blade lineage": ["BLADE_LINEAGE"] }
    Object.entries(unitKeywords).forEach(([internalCode, displayName]) => {
      const lowerDisplay = displayName.toLowerCase()
      if (!unitKeywordToValue.has(lowerDisplay)) {
        unitKeywordToValue.set(lowerDisplay, [])
      }
      unitKeywordToValue.get(lowerDisplay)!.push(internalCode)
    })

    return { keywordToValue, unitKeywordToValue }
  }, [keywordMatch, unitKeywords])
}

// Empty mappings constant for loading state
const EMPTY_MAPPINGS: SearchMappings = {
  keywordToValue: new Map(),
  unitKeywordToValue: new Map(),
}

/**
 * Non-suspending version of useSearchMappings for list filtering.
 * Returns empty mappings while loading - search won't match anything.
 * Use this in list components to prevent suspension during language change.
 *
 * Cards stay visible, search just returns no matches until data loads.
 */
export function useSearchMappingsDeferred(): SearchMappings {
  const { i18n } = useTranslation()

  const { data: keywordMatch } = useQuery(createKeywordMatchQueryOptions(i18n.language))
  const { data: unitKeywords } = useQuery(createUnitKeywordsQueryOptions(i18n.language))

  return useMemo(() => {
    // Return empty mappings while loading
    if (!keywordMatch || !unitKeywords) {
      return EMPTY_MAPPINGS
    }

    const keywordToValue = new Map<string, string[]>()
    const unitKeywordToValue = new Map<string, string[]>()

    Object.entries(keywordMatch).forEach(([internalCode, displayName]) => {
      const lowerDisplay = displayName.toLowerCase()
      if (!keywordToValue.has(lowerDisplay)) {
        keywordToValue.set(lowerDisplay, [])
      }
      keywordToValue.get(lowerDisplay)!.push(internalCode)
    })

    Object.entries(unitKeywords).forEach(([internalCode, displayName]) => {
      const lowerDisplay = displayName.toLowerCase()
      if (!unitKeywordToValue.has(lowerDisplay)) {
        unitKeywordToValue.set(lowerDisplay, [])
      }
      unitKeywordToValue.get(lowerDisplay)!.push(internalCode)
    })

    return { keywordToValue, unitKeywordToValue }
  }, [keywordMatch, unitKeywords])
}
