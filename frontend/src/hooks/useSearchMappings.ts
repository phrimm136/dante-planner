import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import enKeywordMatch from '@static/i18n/EN/keywordMatch.json'
import enTraitMatch from '@static/i18n/EN/traitMatch.json'

export interface SearchMappings {
  keywordToValue: Map<string, string[]>
  traitToValue: Map<string, string[]>
}

export function useSearchMappings(): SearchMappings {
  const { i18n } = useTranslation()

  return useMemo(() => {
    // Load language-specific mappings (only EN has mappings for now)
    const keywordMatch = i18n.language === 'EN' ? enKeywordMatch : ({} as Record<string, string>)
    const traitMatch = i18n.language === 'EN' ? enTraitMatch : ({} as Record<string, string>)

    // Create reverse mappings: natural language -> bracketed values
    const keywordToValue = new Map<string, string[]>()
    const traitToValue = new Map<string, string[]>()

    // Build reverse map for keywords
    Object.entries(keywordMatch).forEach(([bracketedKey, naturalLanguage]) => {
      const lowerNatural = naturalLanguage.toLowerCase()
      if (!keywordToValue.has(lowerNatural)) {
        keywordToValue.set(lowerNatural, [])
      }
      keywordToValue.get(lowerNatural)!.push(bracketedKey)
    })

    // Build reverse map for traits (keys already have brackets)
    Object.entries(traitMatch).forEach(([bracketedKey, naturalLanguage]) => {
      const lowerNatural = naturalLanguage.toLowerCase()
      if (!traitToValue.has(lowerNatural)) {
        traitToValue.set(lowerNatural, [])
      }
      traitToValue.get(lowerNatural)!.push(bracketedKey)
    })

    return { keywordToValue, traitToValue }
  }, [i18n.language])
}
