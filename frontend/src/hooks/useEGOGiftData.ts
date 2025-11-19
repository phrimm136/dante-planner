import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import type { EGOGift } from '@/types/EGOGiftTypes'
import egoGiftSpecList from '@static/data/EGOGiftSpecList.json'
import enEGOGiftNames from '@static/i18n/EN/EGOGiftNameList.json'

export function useEGOGiftData(): EGOGift[] {
  const { i18n } = useTranslation()

  return useMemo(() => {
    // Try to load language-specific names
    let egoGiftNames: Record<string, string> = {}

    // For now, only EN has EGO Gift names, others fall back to ID
    if (i18n.language === 'EN') {
      egoGiftNames = enEGOGiftNames
    }

    // Merge spec data with names
    return Object.entries(egoGiftSpecList).map(([id, spec]) => ({
      id,
      name: egoGiftNames[id] || id, // Fallback to ID if no translation
      category: spec.category,
      keywords: spec.keywords, // Used for search functionality
      themePack: spec.themePack,
      tier: spec.tier,
      enhancement: 0, // Default to 0 for list view
    }))
  }, [i18n.language])
}
