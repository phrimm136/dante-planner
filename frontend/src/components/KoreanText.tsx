import { memo, type ReactNode } from 'react'
import { SCORE_DREAM_VALID_SYLLABLES } from '@/lib/scoreDreamGlyphs'

/**
 * Korean syllable Unicode range: U+AC00 to U+D7A3
 */
const HANGUL_SYLLABLE_START = 0xAC00
const HANGUL_SYLLABLE_END = 0xD7A3

/**
 * Checks if a character is a Korean syllable
 */
function isKoreanSyllable(char: string): boolean {
  const code = char.charCodeAt(0)
  return code >= HANGUL_SYLLABLE_START && code <= HANGUL_SYLLABLE_END
}

/**
 * Checks if S-Core Dream font has a valid glyph for this character
 */
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
 *
 * @example
 * <KoreanText>크랲게 뇌수 담금주</KoreanText>
 * // "랲" will render in Pretendard, rest in S-Core Dream
 */
export const KoreanText = memo(function KoreanText({ children, className }: KoreanTextProps) {
  const result: ReactNode[] = []
  let currentRun = ''
  let currentNeedsFallback = false

  for (const char of children) {
    const needsFallback = isKoreanSyllable(char) && !hasValidGlyph(char)

    if (needsFallback !== currentNeedsFallback && currentRun) {
      // Flush current run
      if (currentNeedsFallback) {
        result.push(
          <span key={result.length} style={{ fontFamily: 'var(--font-pretendard)' }}>
            {currentRun}
          </span>
        )
      } else {
        result.push(currentRun)
      }
      currentRun = ''
    }

    currentRun += char
    currentNeedsFallback = needsFallback
  }

  // Flush remaining run
  if (currentRun) {
    if (currentNeedsFallback) {
      result.push(
        <span key={result.length} style={{ fontFamily: 'var(--font-pretendard)' }}>
          {currentRun}
        </span>
      )
    } else {
      result.push(currentRun)
    }
  }

  return className ? <span className={className}>{result}</span> : <>{result}</>
})
