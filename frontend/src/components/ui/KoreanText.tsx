import { memo, type ReactNode } from 'react'
import { SCORE_DREAM_VALID_SYLLABLES } from './scoreDreamGlyphs'

/**
 * Korean syllable Unicode range: U+AC00 to U+D7A3
 */
const HANGUL_SYLLABLE_START = 0xac00
const HANGUL_SYLLABLE_END = 0xd7a3

function isKoreanSyllable(char: string): boolean {
  const code = char.charCodeAt(0)
  return code >= HANGUL_SYLLABLE_START && code <= HANGUL_SYLLABLE_END
}

function hasValidGlyph(char: string): boolean {
  const code = char.charCodeAt(0)
  return SCORE_DREAM_VALID_SYLLABLES.has(code)
}

interface KoreanTextProps {
  children: string
  className?: string
}

/**
 * Renders Korean text with automatic Pretendard fallback for characters
 * that S-Core Dream doesn't properly support (empty glyphs).
 *
 * S-Core Dream only has ~2,350 of 11,172 Korean syllables.
 * This component wraps unsupported characters in Pretendard font.
 */
export const KoreanText = memo(function KoreanText({ children, className }: KoreanTextProps) {
  const result: ReactNode[] = []
  let currentRun = ''
  let currentNeedsFallback = false

  for (const char of children) {
    const needsFallback = isKoreanSyllable(char) && !hasValidGlyph(char)

    if (needsFallback !== currentNeedsFallback && currentRun) {
      if (currentNeedsFallback) {
        result.push(
          <span key={result.length} style={{ fontFamily: 'var(--font-pretendard)' }}>
            {currentRun}
          </span>,
        )
      } else {
        result.push(currentRun)
      }
      currentRun = ''
    }

    currentRun += char
    currentNeedsFallback = needsFallback
  }

  if (currentRun) {
    if (currentNeedsFallback) {
      result.push(
        <span key={result.length} style={{ fontFamily: 'var(--font-pretendard)' }}>
          {currentRun}
        </span>,
      )
    } else {
      result.push(currentRun)
    }
  }

  return className ? <span className={className}>{result}</span> : <>{result}</>
})
