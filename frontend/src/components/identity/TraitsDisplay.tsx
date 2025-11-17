import { parseBracketNotation } from '@/lib/identityUtils'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

interface TraitsDisplayProps {
  traits: string[]
}

export function TraitsDisplay({ traits }: TraitsDisplayProps) {
  const { i18n } = useTranslation()

  const translatedTraits = useMemo(() => {
    // Dynamically import trait translations based on current language
    const lang = i18n.language.toUpperCase()

    try {
      // Try to load the trait match file for current language
      const traitMatch = require(`@static/i18n/${lang}/traitMatch.json`)

      return traits.map((trait) => {
        // Try to get translation, fallback to parsed bracket notation
        return traitMatch[trait] || parseBracketNotation(trait)
      })
    } catch (error) {
      // Fallback if translation file doesn't exist
      return traits.map((trait) => parseBracketNotation(trait))
    }
  }, [traits, i18n.language])

  if (!traits || traits.length === 0) {
    return null
  }

  return (
    <div className="border rounded p-3">
      <div className="font-semibold text-sm mb-2 text-center">Traits</div>
      <div className="flex flex-wrap gap-2">
        {translatedTraits.map((trait, index) => (
          <span
            key={index}
            className="bg-muted px-3 py-1 rounded-full text-xs border border-border"
          >
            {trait}
          </span>
        ))}
      </div>
    </div>
  )
}
