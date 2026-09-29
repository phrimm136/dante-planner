/**
 * Unity-style rich text rendering helpers.
 *
 * Game data carries Unity rich text tags (e.g. <s>, <color=...>) in i18n
 * strings. These helpers convert them to React elements at render time,
 * preserving the position of each tag within the surrounding text.
 */

import { Fragment, type ReactNode } from 'react'

import { ORANGE_MARK_STYLE } from '@/lib/constants'
import {
  NAMED_STYLE_GRAMMAR,
  STRIKETHROUGH_GRAMMAR,
  TEXT_STYLE,
  tokenizeRichText,
  type RichTextToken,
} from './richText'

function renderToken(token: RichTextToken): ReactNode {
  if (token.kind === 'element') return <s key={`strike-${token.index}`}>{token.content}</s>
  if (token.kind === 'text') return token.value
  return null
}

/**
 * Render Unity <s>...</s> strikethrough, preserving tag position.
 *
 * Splits the input on each balanced <s>...</s> pair: wrapped substrings
 * render inside a real <s> element, surrounding substrings render as
 * plain text in place. Text without a pair is returned unchanged.
 *
 * Flat parser, balanced pairs only. Nested <s> is not supported.
 * Orphan <s> or </s> with no partner render as literal text.
 */
export function applyStrikethrough(text: string): ReactNode {
  if (!text.includes('<s>')) return text

  const tokens = tokenizeRichText(text, STRIKETHROUGH_GRAMMAR)
  if (!tokens.some((token) => token.kind === 'element')) return text

  return <Fragment>{tokens.map(renderToken)}</Fragment>
}

/** Render named `<style>` runs: `OrangeMark` as the client draws it, any other name as its text. */
export function applyTextStyles(text: string): ReactNode {
  if (!text.includes('<style=')) return applyStrikethrough(text)

  return (
    <Fragment>
      {tokenizeRichText(text, NAMED_STYLE_GRAMMAR).map((token) => {
        if (token.kind === 'text') {
          return <Fragment key={token.index}>{applyStrikethrough(token.value)}</Fragment>
        }
        if (token.kind !== 'element') return null
        if (token.value === TEXT_STYLE.orangeMark) {
          return (
            <span key={token.index} data-text-style={token.value} style={ORANGE_MARK_STYLE}>
              {applyStrikethrough(token.content)}
            </span>
          )
        }
        return <Fragment key={token.index}>{applyStrikethrough(token.content)}</Fragment>
      })}
    </Fragment>
  )
}
