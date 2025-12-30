import { useMemo } from 'react'
import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'
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
        const module = await import(`@static/i18n/${language}/keywordMatch.json`)
        const result = KeywordMatchSchema.safeParse(module.default)

        if (!result.success) {
          const isDev = import.meta.env.DEV
          if (isDev) {
            console.error('[keywordMatch] Validation failed:', result.error.issues)
          }
          throw new Error(`[keywordMatch / ${language}] Invalid data structure`)
        }

        return result.data
      } catch (error) {
        // File doesn't exist for this language - return empty object
        if (error instanceof Error && error.message.includes('Unknown variable dynamic import')) {
          return {}
        }
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
        const module = await import(`@static/i18n/${language}/unitKeywords.json`)
        const result = UnitKeywordsSchema.safeParse(module.default)

        if (!result.success) {
          const isDev = import.meta.env.DEV
          if (isDev) {
            console.error('[unitKeywords] Validation failed:', result.error.issues)
          }
          throw new Error(`[unitKeywords / ${language}] Invalid data structure`)
        }

        return result.data
      } catch (error) {
        // File doesn't exist for this language - return empty object
        if (error instanceof Error && error.message.includes('Unknown variable dynamic import')) {
          return {}
        }
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
