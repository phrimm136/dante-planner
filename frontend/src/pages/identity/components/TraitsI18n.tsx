import type { ReactNode } from 'react'

import { useUnitKeywords } from '@/shared/filter'
import { applyStrikethrough, extractLeadingColor } from '@/shared/gameText'

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
  return { key, ...extractLeadingColor(input) }
}

function renderTrait(parsed: ParsedTrait): ReactNode {
  const content = applyStrikethrough(parsed.text)
  return parsed.color ? <span style={{ color: parsed.color }}>{content}</span> : content
}

interface TraitsI18nProps {
  traits: string[]
}

export function TraitsI18n({ traits }: TraitsI18nProps) {
  const traitsI18n = useUnitKeywords()

  const visibleTraits = traits.filter((trait) => !HIDDEN_TRAITS.has(trait))

  const translatedTraits = visibleTraits.flatMap((trait) => {
    const translated = traitsI18n[trait]
    return translated === undefined ? [] : [parseUnityRichText(trait, translated)]
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
