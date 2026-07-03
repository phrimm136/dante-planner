import { useSuspenseQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { createStaticDataQueryOptions } from '@/lib/queryOptions'
import type { BattleKeywords } from '../types/StartBuffTypes'
import { BattleKeywordsSchema } from '../schemas/BattleKeywordsSchemas'
import { BattleKeywordSpecListSchema } from '../schemas/KeywordSchemas'
import { keywordListQueryKeys } from './useKeywordListData'

function createBattleKeywordsI18nQueryOptions(language: string) {
  return createStaticDataQueryOptions(
    keywordListQueryKeys.i18n(language),
    () => import(`@static/i18n/${language}/battleKeywords.json`),
    BattleKeywordsSchema,
    `battleKeywords / ${language}`,
    { keepPrevious: true },
  )
}

function createBattleKeywordsSpecQueryOptions() {
  return createStaticDataQueryOptions(
    keywordListQueryKeys.spec(),
    () => import('@static/data/battleKeywordSpecList.json'),
    BattleKeywordSpecListSchema,
    'battleKeywords spec',
  )
}

/**
 * Hook that loads battle keywords with i18n translations merged with spec data.
 * Loads both spec (iconId, buffType) and i18n (name, desc) in parallel,
 * then merges them into the full BattleKeywords type.
 *
 * Suspends while loading - wrap in Suspense boundary.
 * Used for translating buff keywords like ParryingResultUp, AttackDmgUp, Protection.
 */
export function useBattleKeywords(): { data: BattleKeywords } {
  const { i18n } = useTranslation()

  const { data: i18nData } = useSuspenseQuery(createBattleKeywordsI18nQueryOptions(i18n.language))
  const { data: specData } = useSuspenseQuery(createBattleKeywordsSpecQueryOptions())

  // Merge i18n (name, desc) with spec (iconId, buffType) for each keyword
  const merged: BattleKeywords = {}
  for (const key of Object.keys(i18nData)) {
    const i18nEntry = i18nData[key]
    const specEntry = specData[key]
    merged[key] = {
      name: i18nEntry.name,
      desc: i18nEntry.desc,
      flavor: i18nEntry.flavor,
      iconId: specEntry?.iconId ?? null,
      buffType: specEntry?.buffType ?? 'Neutral',
    }
  }

  return { data: merged }
}

/**
 * Gets translated keyword name from battle keywords data
 * @param keywords - Battle keywords dictionary (validated)
 * @param key - Keyword key (e.g., "ParryingResultUp")
 * @returns Translated name or original key if not found
 */
export function getKeywordName(keywords: BattleKeywords, key: string): string {
  return keywords[key]?.name ?? key
}
