import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { KeywordMatchSchema } from '../schemas/SearchMappingSchemas'
import { useUnitKeywords } from './useUnitKeywords'

// Query key factory for search mappings
// Hand-rolled: tuples lack the 'list'/'i18n' segments the shared factory produces
export const searchMappingsQueryKeys = {
  all: ['searchMappings'] as const,
  keywordMatch: (language: string) =>
    [...searchMappingsQueryKeys.all, 'keyword', language] as const,
}

export function createKeywordMatchQueryOptions(language: string) {
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
  )
}

function appendToBucket<K, V>(map: Map<K, V[]>, key: K, value: V): void {
  const bucket = map.get(key)
  if (bucket) {
    bucket.push(value)
  } else {
    map.set(key, [value])
  }
}

/**
 * Invert an internal-code → display-name record into lowercased display name →
 * every internal code that renders as it.
 *
 * `{ "Burst": "Rupture" }` becomes `{ "rupture": ["Burst"] }`. Distinct codes
 * can share a display name, so the value is a bucket rather than a scalar.
 */
export function buildReverseMap(byInternalCode: Record<string, string>): Map<string, string[]> {
  const reverse = new Map<string, string[]>()
  for (const [internalCode, displayName] of Object.entries(byInternalCode)) {
    appendToBucket(reverse, displayName.toLowerCase(), internalCode)
  }
  return reverse
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
  const unitKeywords = useUnitKeywords()

  return {
    keywordToValue: buildReverseMap(keywordMatch),
    unitKeywordToValue: buildReverseMap(unitKeywords),
  }
}
