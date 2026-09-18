import { useEntityListSpec, useEntityListI18n } from '@/shared/entityCatalog'
import type { BattleKeywords } from '../types/StartBuffTypes'
import { KEYWORD_LIST } from './useKeywordListData'

export function useBattleKeywords(): { data: BattleKeywords } {
  const specData = useEntityListSpec(KEYWORD_LIST)
  const i18nData = useEntityListI18n(KEYWORD_LIST)

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

export function getKeywordName(keywords: BattleKeywords, key: string): string {
  return keywords[key]?.name ?? key
}
