import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import egoSpecList from '@static/data/EGOSpecList.json'
import enEGONames from '@static/i18n/EN/EGONameList.json'
import type { EGO, EGORank } from '@/types/EGOTypes'

export function useEGOData(): EGO[] {
  const { i18n } = useTranslation()

  return useMemo(() => {
    // Try to load language-specific names
    let egoNames: Record<string, string> = {}

    // For now, only EN has EGO names, others fall back to ID
    if (i18n.language === 'EN') {
      egoNames = enEGONames
    }

    // Merge spec data with names
    return Object.entries(egoSpecList).map(([id, spec]) => ({
      id,
      name: egoNames[id] || id, // Fallback to ID if no translation
      rank: spec.rank as EGORank, // Data already in PascalCase
      sin: spec.sin, // Sin affinity for panel coloring
      sinner: spec.sinner, // PascalCase without brackets
      keywords: spec.keywords, // PascalCase without brackets
    }))
  }, [i18n.language])
}
