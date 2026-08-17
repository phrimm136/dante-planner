/**
 * ColoredText — Unity rich text with six-digit hex colors.
 *
 * Nested colors, malformed close tags and size blocks all resolve here.
 *
 * Used by: AbEventDetailPage, ThemePackCard, formatBuffDescription, etc.
 */

import {
  HEX_COLOR_SOURCE,
  NESTED_HEX_COLOR_GRAMMAR,
  RICH_TEXT_TAG,
  tokenizeRichText,
  type RichTextToken,
} from '../lib/richText'

const COLOR_PAIR_RE = new RegExp(`<color=${HEX_COLOR_SOURCE}>([^<]*)</color>`, 'g')

function renderTokens(tokens: RichTextToken[]): React.ReactNode[] {
  return tokens.map((token) => {
    switch (token.kind) {
      case 'text':
        return token.value
      case 'break':
        return <br key={`break-${token.index}`} />
      case 'element':
        return token.name === RICH_TEXT_TAG.size ? (
          <span key={`size-${token.index}`} className="text-[75%]">
            {renderTokens(token.children)}
          </span>
        ) : (
          <span key={`color-${token.index}`} style={{ color: token.value }}>
            {renderTokens(token.children)}
          </span>
        )
    }
  })
}

/**
 * Parse Unity color and size tags into React nodes, nesting included.
 *
 * This is the single consolidated tag processing function.
 * All color tag parsing in the codebase should use this.
 */
export function parseColorTags(text: string): React.ReactNode[] {
  return renderTokens(tokenizeRichText(text, NESTED_HEX_COLOR_GRAMMAR))
}

/** Strip all color tags, keeping inner text */
export function stripColorTags(text: string): string {
  // Repeatedly strip until no tags remain (handles nested)
  let result = text
  let prev = ''
  while (result !== prev) {
    prev = result
    result = result.replace(COLOR_PAIR_RE, '$1')
  }
  return result
}

/**
 * ColoredText component — renders Unity rich text as JSX.
 * Handles <color>, <size>, nested tags, and malformed close tags.
 */
export function ColoredText({ text }: { text: string }) {
  return <>{parseColorTags(text)}</>
}
