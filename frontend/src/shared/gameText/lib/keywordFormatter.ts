import type {
  KeywordType,
  ResolvedKeyword,
  ParsedSegment,
  KeywordResolutionContext,
} from '../types/KeywordTypes'

/**
 * Regex pattern for matching [BracketedKeywords] in text
 * Captures the content inside brackets (excluding the brackets themselves)
 */
const KEYWORD_PATTERN = /\[([^\]]+)\]/g

/**
 * Reads a record entry only when the key is an own property, so keys taken from
 * description text cannot reach `Object.prototype` members.
 */
function ownEntry<T>(record: Record<string, T>, key: string): T | undefined {
  return Object.hasOwn(record, key) ? record[key] : undefined
}

export function parseKeywords(text: string): ParsedSegment[] {
  const segments: ParsedSegment[] = []
  let lastIndex = 0

  // Use matchAll to avoid global regex state issues
  for (const match of text.matchAll(KEYWORD_PATTERN)) {
    const bracketed = match[0]

    if (match.index > lastIndex) {
      segments.push({
        type: 'text',
        content: text.slice(lastIndex, match.index),
      })
    }

    segments.push({
      type: 'keyword',
      content: bracketed.slice(1, -1),
    })

    lastIndex = match.index + bracketed.length
  }

  if (lastIndex < text.length) {
    segments.push({
      type: 'text',
      content: text.slice(lastIndex),
    })
  }

  return segments
}

export function resolveKeywordType(
  key: string,
  battleKeywords: KeywordResolutionContext['battleKeywords'],
  skillTags: KeywordResolutionContext['skillTags'],
): KeywordType {
  if (Object.hasOwn(battleKeywords, key)) {
    return 'battleKeyword'
  }
  if (Object.hasOwn(skillTags, key)) {
    return 'skillTag'
  }
  return 'unknown'
}

export function getKeywordColor(
  key: string,
  type: KeywordType,
  colorCodes: KeywordResolutionContext['colorCodes'],
  battleKeywords: KeywordResolutionContext['battleKeywords'],
): string {
  if (type === 'battleKeyword') {
    const buffType = ownEntry(battleKeywords, key)?.buffType
    const buffColor = buffType ? ownEntry(colorCodes, buffType) : undefined
    return buffColor ?? colorCodes['Critical'] ?? ''
  }

  if (type === 'skillTag') {
    return ownEntry(colorCodes, key) ?? colorCodes['Critical'] ?? ''
  }

  return ''
}

export function resolveKeyword(key: string, context: KeywordResolutionContext): ResolvedKeyword {
  const { battleKeywords, skillTags, colorCodes } = context

  const type = resolveKeywordType(key, battleKeywords, skillTags)
  const color = getKeywordColor(key, type, colorCodes, battleKeywords)

  const keywordData = ownEntry(battleKeywords, key)
  if (type === 'battleKeyword' && keywordData) {
    return {
      type,
      key,
      displayText: keywordData.name,
      description: keywordData.desc,
      ...(keywordData.flavor !== undefined && { flavor: keywordData.flavor }),
      iconId: keywordData.iconId,
      buffType: keywordData.buffType,
      color,
    }
  }

  const tagText = ownEntry(skillTags, key)
  if (type === 'skillTag' && tagText !== undefined) {
    return {
      type,
      key,
      displayText: tagText,
      color,
    }
  }

  return {
    type: 'unknown',
    key,
    displayText: `[${key}]`,
    color: '',
  }
}

export function formatDescription(
  text: string,
  context: KeywordResolutionContext,
): ParsedSegment[] {
  const segments = parseKeywords(text)

  return segments.map((segment) => {
    if (segment.type === 'keyword') {
      return {
        ...segment,
        keyword: resolveKeyword(segment.content, context),
      }
    }
    return segment
  })
}
