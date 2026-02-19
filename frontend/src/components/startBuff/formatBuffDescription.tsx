import type { BuffEffect, StartBuffI18n, BattleKeywords } from '@/types/StartBuffTypes'
import { getKeywordName } from '@/hooks/useBattleKeywords'

/**
 * Parses color tags and converts to React elements
 * @param text - Text with <color=#hex>content</color> tags
 * @returns Array of React elements with styled spans
 */
export function parseColorTags(text: string): React.ReactNode[] {
  const parts: React.ReactNode[] = []
  const openTagRegex = /<color=(#[0-9a-fA-F]{6})>/
  let lastIndex = 0

  while (lastIndex < text.length) {
    const slice = text.slice(lastIndex)
    const match = openTagRegex.exec(slice)
    if (!match) {
      parts.push(slice)
      break
    }

    const tagStart = lastIndex + match.index
    const color = match[1]
    const contentStart = tagStart + match[0].length

    if (tagStart > lastIndex) {
      parts.push(text.slice(lastIndex, tagStart))
    }

    // Find matching </color> using depth counter to handle nesting
    let depth = 1
    let pos = contentStart
    while (pos < text.length && depth > 0) {
      if (text.startsWith('<color=', pos)) {
        depth++
        const gtIndex = text.indexOf('>', pos)
        pos = gtIndex === -1 ? text.length : gtIndex + 1
      } else if (text.startsWith('</color>', pos)) {
        depth--
        if (depth > 0) pos += '</color>'.length
      } else {
        pos++
      }
    }

    parts.push(
      <span key={tagStart} style={{ color }}>
        {parseColorTags(text.slice(contentStart, pos))}
      </span>
    )

    lastIndex = pos + '</color>'.length
  }

  return parts
}

/**
 * Replaces placeholders in text with effect values
 * Placeholders: {0} = value, {1} = value2
 * For referenceData: {0} = activeRound, {1} = buffKeyword (translated), {2} = stack, {3} = turn, {4} = limit
 */
function replacePlaceholders(text: string, effect: BuffEffect, battleKeywords?: BattleKeywords): string {
  let result = text

  if (effect.referenceData) {
    const { activeRound, buffKeyword, stack, turn, limit } = effect.referenceData
    if (activeRound !== undefined) result = result.replace('{0}', String(activeRound))
    if (buffKeyword !== undefined) {
      const translatedKeyword = battleKeywords
        ? getKeywordName(battleKeywords, buffKeyword)
        : buffKeyword
      result = result.replace('{1}', translatedKeyword)
    }
    if (stack !== undefined) result = result.replace('{2}', String(stack))
    if (turn !== undefined) result = result.replace('{3}', String(turn))
    if (limit !== undefined) result = result.replace('{4}', String(limit))
  } else {
    if (effect.value !== undefined) result = result.replace('{0}', String(effect.value))
    if (effect.value2 !== undefined) result = result.replace('{1}', String(effect.value2))
  }

  return result
}

/**
 * Formats a single buff effect into displayable text with colors
 * @param effect - Buff effect data
 * @param i18n - i18n translations
 * @param battleKeywords - Battle keywords for translating buff keywords
 * @returns Formatted React node with color styling
 */
export function formatEffect(
  effect: BuffEffect,
  i18n: StartBuffI18n,
  battleKeywords?: BattleKeywords
): React.ReactNode {
  // Use customLocalizeTextId if present (for enhanced effects), otherwise use type
  const translationKey = effect.customLocalizeTextId || effect.type
  const template = i18n[translationKey] || translationKey

  // Replace placeholders with values (including translated buffKeyword)
  const text = replacePlaceholders(template, effect, battleKeywords)

  // Parse color tags and return React elements
  return parseColorTags(text)
}

/**
 * Formats all buff effects into a list of displayable items
 * @param effects - Array of buff effects
 * @param i18n - i18n translations
 * @param battleKeywords - Battle keywords for translating buff keywords
 * @returns Array of formatted React nodes
 */
export function formatBuffEffects(
  effects: BuffEffect[],
  i18n: StartBuffI18n,
  battleKeywords?: BattleKeywords
): React.ReactNode[] {
  return effects.map((effect, index) => (
    <div key={index} className="text-[16.9px] leading-5">
      •{formatEffect(effect, i18n, battleKeywords)}
    </div>
  ))
}
