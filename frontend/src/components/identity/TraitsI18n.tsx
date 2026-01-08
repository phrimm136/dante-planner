import type { ReactNode } from 'react'

import { useTraitsI18n } from '@/hooks/useTraitsI18n'

/** Keywords to skip in trait display (internal/visual only) */
const HIDDEN_TRAITS = new Set(['BASE_APPEARANCE', 'SMALL'])

interface ParsedTrait {
  key: string
  text: string
  color?: string
  strikethrough?: boolean
}

/**
 * Parse Unity-style rich text to structured data
 * e.g., "<color=#d40000><s>Jia Family</s></color>" -> { text: "Jia Family", color: "#d40000", strikethrough: true }
 */
function parseUnityRichText(key: string, input: string): ParsedTrait {
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

  return { key, text, color, strikethrough }
}

/** Render parsed trait as React element */
function renderTrait(parsed: ParsedTrait): ReactNode {
  const style = parsed.color ? { color: parsed.color } : undefined
  const content = parsed.strikethrough ? <s>{parsed.text}</s> : parsed.text

  return style ? <span style={style}>{content}</span> : content
}

interface TraitsI18nProps {
  /** Trait IDs to translate */
  traits: string[]
}

/**
 * Suspending component that renders translated trait badges.
 * Uses useSuspenseQuery internally - MUST be wrapped in Suspense boundary.
 *
 * Filters hidden traits and parses Unity-style rich text.
 * This allows granular loading: trait container stays visible while only
 * the trait badges show skeleton during language change.
 *
 * @example
 * <Suspense fallback={<Skeleton className="h-6 w-full" />}>
 *   <TraitsI18n traits={identity.unitKeywordList} />
 * </Suspense>
 */
export function TraitsI18n({ traits }: TraitsI18nProps) {
  const traitsI18n = useTraitsI18n()

  // Filter out hidden traits
  const visibleTraits = traits.filter((trait) => !HIDDEN_TRAITS.has(trait))

  // Map to translated and parsed traits
  const translatedTraits = visibleTraits.map((trait) => {
    const translated = traitsI18n[trait] || trait
    return parseUnityRichText(trait, translated)
  })

  if (translatedTraits.length === 0) {
    return null
  }

  return (
    <div className="flex flex-wrap gap-1">
      {translatedTraits.map((trait) => (
        <span
          key={trait.key}
          className="px-2 py-0.5 text-xs bg-secondary text-secondary-foreground border border-border"
        >
          {renderTrait(trait)}
        </span>
      ))}
    </div>
  )
}
