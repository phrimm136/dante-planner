import type { ReactNode } from 'react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

/** Keywords to skip in trait display (internal/visual only) */
const HIDDEN_TRAITS = new Set(['BASE_APPEARANCE', 'SMALL'])

interface ParsedTrait {
  text: string
  color?: string
  strikethrough?: boolean
}

/**
 * Parse Unity-style rich text to structured data
 * e.g., "<color=#d40000><s>Jia Family</s></color>" -> { text: "Jia Family", color: "#d40000", strikethrough: true }
 */
function parseUnityRichText(input: string): ParsedTrait {
  let text = input
  let color: string | undefined
  let strikethrough = false

  // Extract color
  const colorMatch = text.match(/<color=([^>]+)>/)
  if (colorMatch) {
    color = colorMatch[1]
    text = text.replace(/<color=[^>]+>/g, '').replace(/<\/color>/g, '')
  }

  // Check for strikethrough
  if (text.includes('<s>')) {
    strikethrough = true
    text = text.replace(/<s>/g, '').replace(/<\/s>/g, '')
  }

  return { text, color, strikethrough }
}

/** Render parsed trait as React element */
function renderTrait(parsed: ParsedTrait): ReactNode {
  const style = parsed.color ? { color: parsed.color } : undefined
  const content = parsed.strikethrough ? <s>{parsed.text}</s> : parsed.text

  return style ? <span style={style}>{content}</span> : content
}

interface TraitsDisplayProps {
  traits: string[]
}

export function TraitsDisplay({ traits }: TraitsDisplayProps) {
  const { t, i18n } = useTranslation()
  const [translatedTraits, setTranslatedTraits] = useState<ParsedTrait[]>([])

  useEffect(() => {
    if (!traits || traits.length === 0) return

    const load = async () => {
      const lang = i18n.language

      // Filter out hidden traits
      const visibleTraits = traits.filter((trait) => !HIDDEN_TRAITS.has(trait))

      try {
        const response = await fetch(`/i18n/${lang}/unitKeywords.json`)
        if (!response.ok) throw new Error(`Failed to fetch unitKeywords: ${response.status}`)
        const unitKeywords = await response.json()

        setTranslatedTraits(
          visibleTraits.map((trait) => {
            const translated = unitKeywords[trait] || trait
            return parseUnityRichText(translated)
          })
        )
      } catch {
        // Fallback: use raw trait names
        setTranslatedTraits(visibleTraits.map((t) => ({ text: t })))
      }
    }

    load()
  }, [traits, i18n.language])

  if (translatedTraits.length === 0) {
    return null
  }

  return (
    <div className="border rounded p-3">
      <div className="font-semibold text-sm mb-2 text-center">{t('identity.unitKeyword')}</div>
      <div className="flex flex-wrap gap-1">
        {translatedTraits.map((trait, index) => (
          <span
            key={index}
            className="px-2 py-0.5 text-xs bg-secondary text-secondary-foreground border border-border"
          >
            {renderTrait(trait)}
          </span>
        ))}
      </div>
    </div>
  )
}
