/**
 * Unity-style rich text rendering helpers.
 *
 * Game data carries Unity rich text tags (e.g. <s>, <color=...>) in i18n
 * strings. These helpers convert them to React elements at render time,
 * preserving the position of each tag within the surrounding text.
 */

import { Fragment, type ReactNode } from 'react'

import { STRIKETHROUGH_GRAMMAR, tokenizeRichText, type RichTextToken } from './richText'

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
 *
 * @example
 * applyStrikethrough('Apply 2 <s>Sinking</s> potency')
 *   // => 'Apply 2 ' + <s>Sinking</s> + ' potency'
 */
export function applyStrikethrough(text: string): ReactNode {
  if (!text.includes('<s>')) return text

  const tokens = tokenizeRichText(text, STRIKETHROUGH_GRAMMAR)
  if (!tokens.some((token) => token.kind === 'element')) return text

  return <Fragment>{tokens.map(renderToken)}</Fragment>
}
