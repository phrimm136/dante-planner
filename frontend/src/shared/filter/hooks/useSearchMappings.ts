import { useMemo } from 'react'
import { useSuspenseQuery, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { KeywordMatchSchema, UnitKeywordsSchema } from '../schemas/SearchMappingSchemas'

// Query key factory for search mappings
// Hand-rolled: tuples lack the 'list'/'i18n' segments the shared factory produces
export const searchMappingsQueryKeys = {
  all: ['searchMappings'] as const,
  keywordMatch: (language: string) => [...searchMappingsQueryKeys.all, 'keyword', language] as const,
  unitKeywords: (language: string) => [...searchMappingsQueryKeys.all, 'unit', language] as const,
}

function createKeywordMatchQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    searchMappingsQueryKeys.keywordMatch(language),
    async () => {
      try {
        return await import(`@static/i18n/${language}/keywordMatch.json`)
      } catch {
        // Missing language file falls back to empty mappings, not an error
        return { default: {} }
      }
    },
    KeywordMatchSchema,
    `keywordMatch / ${language}`,
    { keepPrevious: true },
  )
}

function createUnitKeywordsQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    searchMappingsQueryKeys.unitKeywords(language),
    async () => {
      try {
        return await import(`@static/i18n/${language}/unitKeywords.json`)
      } catch {
        // Missing language file falls back to empty mappings, not an error
        return { default: {} }
      }
    },
    UnitKeywordsSchema,
    `unitKeywords / ${language}`,
    { keepPrevious: true },
  )
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
