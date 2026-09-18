import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { entityListI18nOptions, type EntityListDataConfig } from '@/shared/entityCatalog'
import { buildReverseMap, createKeywordMatchQueryOptions } from './useSearchMappings'
import type { SearchMappings } from './useSearchMappings'
import { createUnitKeywordsQueryOptions } from './useUnitKeywords'

const EMPTY_MAPPINGS: SearchMappings = {
  keywordToValue: new Map(),
  unitKeywordToValue: new Map(),
}

export function useSearchTermSources<TSpec, TI18n>(
  cfg: EntityListDataConfig<TSpec, TI18n>,
  emptyNames: TI18n,
  withKeywordMappings = true,
): { names: TI18n; mappings: SearchMappings } {
  const { i18n } = useTranslation()
  const language = i18n.language

  const { data: names } = useQuery(entityListI18nOptions(cfg, language))
  const { data: keywordMatch } = useQuery({
    ...createKeywordMatchQueryOptions(language),
    enabled: withKeywordMappings,
  })
  const { data: unitKeywords } = useQuery({
    ...createUnitKeywordsQueryOptions(language),
    enabled: withKeywordMappings,
  })

  return {
    names: names ?? emptyNames,
    mappings:
      keywordMatch && unitKeywords
        ? {
            keywordToValue: buildReverseMap(keywordMatch),
            unitKeywordToValue: buildReverseMap(unitKeywords),
          }
        : EMPTY_MAPPINGS,
  }
}
