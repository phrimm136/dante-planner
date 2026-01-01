/**
 * Keyword Formatter
 *
 * Pure functions for parsing and resolving [BracketedKeywords] in skill/passive descriptions.
 * No React hooks - these are pure utilities for use by the useKeywordFormatter hook.
 *
 * Keyword types:
 * - Battle keywords: Icon + colored name + popover description
 * - Skill tags: Colored display text only
 * - Unknown: Plain text with brackets preserved
 */

import type {
  KeywordType,
  ResolvedKeyword,
  ParsedSegment,
  KeywordResolutionContext,
} from '@/types/KeywordTypes'

/**
 * Regex pattern for matching [BracketedKeywords] in text
 * Captures the content inside brackets (excluding the brackets themselves)
 */
const KEYWORD_PATTERN = /\[([^\]]+)\]/g

/**
 * Parses description text into segments of plain text and keyword references.
 *
 * Uses String.matchAll() to avoid global regex state issues.
 *
 * @param text - Raw description text with [BracketedKeywords]
 * @returns Array of parsed segments (text or keyword)
 *
 * @example
 * parseKeywords("Apply 2 [Sinking] next turn")
 * // => [
 * //   { type: 'text', content: 'Apply 2 ' },
 * //   { type: 'keyword', content: 'Sinking' },
 * //   { type: 'text', content: ' next turn' }
 * // ]
 */
export function parseKeywords(text: string): ParsedSegment[] {
  const segments: ParsedSegment[] = []
  let lastIndex = 0

  // Use matchAll to avoid global regex state issues
  for (const match of text.matchAll(KEYWORD_PATTERN)) {
    // Add text before this keyword
    if (match.index > lastIndex) {
      segments.push({
        type: 'text',
        content: text.slice(lastIndex, match.index),
      })
    }

    // Add keyword segment (content is the captured group, not the brackets)
    segments.push({
      type: 'keyword',
      content: match[1],
    })

    lastIndex = match.index + match[0].length
  }

  // Add remaining text after last keyword
  if (lastIndex < text.length) {
    segments.push({
      type: 'text',
      content: text.slice(lastIndex),
    })
  }

  return segments
}

/**
 * Determines the type of a keyword by checking data sources.
 *
 * @param key - Keyword key from brackets (e.g., "Sinking", "OnSucceedAttack")
 * @param battleKeywords - Battle keywords dictionary
 * @param skillTags - Skill tags dictionary
 * @returns Keyword type: 'battleKeyword', 'skillTag', or 'unknown'
 *
 * @example
 * resolveKeywordType("Sinking", battleKeywords, skillTags)
 * // => 'battleKeyword'
 */
export function resolveKeywordType(
  key: string,
  battleKeywords: KeywordResolutionContext['battleKeywords'],
  skillTags: KeywordResolutionContext['skillTags']
): KeywordType {
  if (Object.hasOwn(battleKeywords, key)) {
    return 'battleKeyword'
  }
  if (Object.hasOwn(skillTags, key)) {
    return 'skillTag'
  }
  return 'unknown'
}

/**
 * Gets the color for a keyword based on its type and data.
 *
 * @param key - Keyword key
 * @param type - Resolved keyword type
 * @param colorCodes - Color code mapping
 * @param battleKeywords - Battle keywords dictionary (for buffType lookup)
 * @returns Hex color string or empty string for inherit
 *
 * Color resolution:
 * - Battle keyword: colorCodes[buffType] (Positive/Negative/Neutral)
 * - Skill tag: colorCodes[key] ?? colorCodes['Critical'] (green fallback)
 * - Unknown: '' (inherit parent color)
 */
export function getKeywordColor(
  key: string,
  type: KeywordType,
  colorCodes: KeywordResolutionContext['colorCodes'],
  battleKeywords: KeywordResolutionContext['battleKeywords']
): string {
  if (type === 'battleKeyword') {
    const buffType = battleKeywords[key]?.buffType
    if (buffType && Object.hasOwn(colorCodes, buffType)) {
      return colorCodes[buffType]
    }
    // Fallback for battle keywords without buffType
    return colorCodes['Critical'] ?? ''
  }

  if (type === 'skillTag') {
    // Try key-specific color, fallback to Critical (green)
    if (Object.hasOwn(colorCodes, key)) {
      return colorCodes[key]
    }
    return colorCodes['Critical'] ?? ''
  }

  // Unknown keywords inherit parent color
  return ''
}

/**
 * Resolves a keyword key into complete rendering data.
 *
 * Main resolution function combining type detection, translation, and color lookup.
 * Uses forgiving strategy: returns partial data rather than failing.
 *
 * @param key - Keyword key from brackets
 * @param context - Resolution context with all data sources
 * @returns Complete ResolvedKeyword object for rendering
 *
 * @example
 * resolveKeyword("Sinking", { battleKeywords, skillTags, colorCodes })
 * // => {
 * //   type: 'battleKeyword',
 * //   key: 'Sinking',
 * //   displayText: 'Sinking',
 * //   description: 'Each turn, lose HP...',
 * //   iconId: 'Sinking',
 * //   buffType: 'Negative',
 * //   color: '#e30000'
 * // }
 */
export function resolveKeyword(
  key: string,
  context: KeywordResolutionContext
): ResolvedKeyword {
  const { battleKeywords, skillTags, colorCodes } = context

  const type = resolveKeywordType(key, battleKeywords, skillTags)
  const color = getKeywordColor(key, type, colorCodes, battleKeywords)

  // Battle keyword: full data with icon and description
  if (type === 'battleKeyword') {
    const keywordData = battleKeywords[key]
    return {
      type,
      key,
      displayText: keywordData.name,
      description: keywordData.desc,
      iconId: keywordData.iconId,
      buffType: keywordData.buffType,
      color,
    }
  }

  // Skill tag: display text only
  if (type === 'skillTag') {
    return {
      type,
      key,
      displayText: skillTags[key],
      color,
    }
  }

  // Unknown: preserve brackets, no styling
  return {
    type: 'unknown',
    key,
    displayText: `[${key}]`,
    color: '',
  }
}

/**
 * Parses and resolves all keywords in a description text.
 *
 * Combines parsing and resolution into a single operation.
 * Each keyword segment gets its resolved data attached.
 *
 * @param text - Raw description text with [BracketedKeywords]
 * @param context - Resolution context with all data sources
 * @returns Array of parsed segments with resolved keyword data
 *
 * @example
 * formatDescription("Apply 2 [Sinking] [OnSucceedAttack]", context)
 * // => [
 * //   { type: 'text', content: 'Apply 2 ' },
 * //   { type: 'keyword', content: 'Sinking', keyword: { type: 'battleKeyword', ... } },
 * //   { type: 'text', content: ' ' },
 * //   { type: 'keyword', content: 'OnSucceedAttack', keyword: { type: 'skillTag', ... } }
 * // ]
 */
export function formatDescription(
  text: string,
  context: KeywordResolutionContext
): ParsedSegment[] {
  const segments = parseKeywords(text)

  return segments.map(segment => {
    if (segment.type === 'keyword') {
      return {
        ...segment,
        keyword: resolveKeyword(segment.content, context),
      }
    }
    return segment
  })
}
