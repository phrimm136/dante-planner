import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import identitySpecList from '@static/data/identitySpecList.json'
import enIdentityNames from '@static/i18n/EN/identityNameList.json'

export interface Identity {
  id: string
  name: string
  star: number
  sinner: string
  traits: string[]
  keywords: string[]
}

export function useIdentityData(): Identity[] {
  const { i18n } = useTranslation()

  return useMemo(() => {
    // Try to load language-specific names
    let identityNames: Record<string, string> = {}

    // For now, only EN has identity names, others fall back to ID
    if (i18n.language === 'EN') {
      identityNames = enIdentityNames
    }

    // Merge spec data with names
    return Object.entries(identitySpecList).map(([id, spec]) => ({
      id,
      name: identityNames[id] || id, // Fallback to ID if no translation
      star: spec.star,
      sinner: spec.sinner,
      traits: spec.traits,
      keywords: spec.keywords,
    }))
  }, [i18n.language])
}
