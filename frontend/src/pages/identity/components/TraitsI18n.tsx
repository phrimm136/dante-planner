import type { ReactNode } from 'react'

import { useTraitsI18n } from '../hooks/useTraitsI18n'
import { applyStrikethrough } from '@/shared/gameText'

/** Keywords to skip in trait display (internal/visual only) */
const HIDDEN_TRAITS = new Set(['BASE_APPEARANCE', 'SMALL'])

interface ParsedTrait {
  key: string
  /** Text with <color> stripped; may still contain <s>...</s> tags */
  text: string
  color?: string
}

/**
 * Strip the outer <color=...> wrapper, leaving any inner <s> tags
 * for {@link applyStrikethrough} to handle at render time.
 *
 * @example "<color=#d40000><s>Jia Family</s></color>"
 *       -> { color: "#d40000", text: "<s>Jia Family</s>" }
 */
function parseUnityRichText(key: string, input: string): ParsedTrait {
  const colorMatch = input.match(/<color=([^>]+)>/)
  if (!colorMatch) return { key, text: input }

  const color = colorMatch[1]
  const text = input.replace(/<color=[^>]+>/g, '').replace(/<\/color>/g, '')
  return { key, text, color }
}

function renderTrait(parsed: ParsedTrait): ReactNode {
  const content = applyStrikethrough(parsed.text)
  return parsed.color ? <span style={{ color: parsed.color }}>{content}</span> : content
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

  // Map to translated and parsed traits, filtering out traits without translations
  const translatedTraits = visibleTraits
    .filter((trait) => traitsI18n[trait] !== undefined)
    .map((trait) => {
      const translated = traitsI18n[trait]
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
