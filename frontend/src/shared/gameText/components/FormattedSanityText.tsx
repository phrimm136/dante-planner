/**
 * FormattedSanityText - Renders sanity condition text with proper formatting
 *
 * Parses Unity-style tags and converts them to React elements:
 * - <size=95%>...</size> → smaller text
 * - <color=red>...</color> → colored text
 * - \n → line breaks
 */

import { Fragment } from 'react'

import {
  FLAT_ANY_COLOR_GRAMMAR,
  RICH_TEXT_TAG,
  tokenizeRichText,
  type RichTextToken,
} from '../lib/richText'

interface FormattedSanityTextProps {
  text: string
  className?: string
}

function renderTokens(tokens: RichTextToken[], small: boolean): React.ReactNode[] {
  return tokens.flatMap((token) => {
    switch (token.kind) {
      case 'break':
        return <br key={`break-${token.index}`} />
      case 'text':
        return small ? (
          <span key={`size-${token.index}`} className="text-xs">
            {token.value}
          </span>
        ) : (
          <Fragment key={`text-${token.index}`}>{token.value}</Fragment>
        )
      case 'element':
        return token.name === RICH_TEXT_TAG.size ? (
          renderTokens(token.children, true)
        ) : (
          <span key={`color-${token.index}`} style={{ color: token.value }}>
            {token.content}
          </span>
        )
    }
  })
}

/**
 * Renders sanity condition text with size/color formatting preserved
 */
export function FormattedSanityText({ text, className }: FormattedSanityTextProps) {
  if (!text) return null

  return (
    <span className={className}>
      {renderTokens(tokenizeRichText(text, FLAT_ANY_COLOR_GRAMMAR), false)}
    </span>
  )
}
