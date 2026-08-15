import { useEntityListData } from '@/shared/entityCatalog'
import type { BattleKeywords } from '../types/StartBuffTypes'
import { KEYWORD_LIST } from './useKeywordListData'

/**
 * Hook that loads battle keywords with i18n translations merged with spec data.
 * Loads both spec (iconId, buffType) and i18n (name, desc) in parallel,
 * then merges them into the full BattleKeywords type.
 *
 * Suspends while loading - wrap in Suspense boundary.
 * Used for translating buff keywords like ParryingResultUp, AttackDmgUp, Protection.
 */
export function useBattleKeywords(): { data: BattleKeywords } {
  const { spec: specData, i18n: i18nData } = useEntityListData(KEYWORD_LIST)

  // Merge i18n (name, desc) with spec (iconId, buffType) for each keyword
  const merged: BattleKeywords = {}
  for (const [key, i18nEntry] of Object.entries(i18nData)) {
    const specEntry = specData[key]
    merged[key] = {
      name: i18nEntry.name,
      desc: i18nEntry.desc,
      ...(i18nEntry.flavor !== undefined && { flavor: i18nEntry.flavor }),
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
