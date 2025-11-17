import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

interface TraitsDisplayProps {
  traits: string[]
}

export function TraitsDisplay({ traits }: TraitsDisplayProps) {
  const { i18n } = useTranslation()
  const [translatedTraits, setTranslatedTraits] = useState<string[]>(traits)

  useEffect(() => {
    if (!traits || traits.length === 0) return

    const load = async () => {
      const lang = i18n.language

      try {
        const traitMatch = (await import(`@static/i18n/${lang}/traitMatch.json`)).default

        setTranslatedTraits(traits.map((trait) => traitMatch[trait]))
      } catch {
        // Fallback: no translation file
        setTranslatedTraits(traits)
      }
    }

    load()
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
