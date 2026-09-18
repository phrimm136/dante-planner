import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import { KeywordMatchSchema } from '../schemas/SearchMappingSchemas'
import { useUnitKeywords } from './useUnitKeywords'

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

export function buildReverseMap(byInternalCode: Record<string, string>): Map<string, string[]> {
  const reverse = new Map<string, string[]>()
  for (const [internalCode, displayName] of Object.entries(byInternalCode)) {
    appendToBucket(reverse, displayName.toLowerCase(), internalCode)
  }
  return reverse
}

export interface SearchMappings {
  keywordToValue: Map<string, string[]>
  unitKeywordToValue: Map<string, string[]>
}

export function useSearchMappings(): SearchMappings {
  const { i18n } = useTranslation()

  const { data: keywordMatch } = useSuspenseQuery(createKeywordMatchQueryOptions(i18n.language))
  const unitKeywords = useUnitKeywords()

  return {
    keywordToValue: buildReverseMap(keywordMatch),
    unitKeywordToValue: buildReverseMap(unitKeywords),
  }
}
