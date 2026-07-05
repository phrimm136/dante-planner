/**
 * Unity-style rich text rendering helpers.
 *
 * Game data carries Unity rich text tags (e.g. <s>, <color=...>) in i18n
 * strings. These helpers convert them to React elements at render time,
 * preserving the position of each tag within the surrounding text.
 */

import { Fragment, type ReactNode } from 'react'

const STRIKETHROUGH_RE = /<s>([\s\S]*?)<\/s>/g

/**
 * Render Unity <s>...</s> strikethrough, preserving tag position.
 *
 * Splits the input on each balanced <s>...</s> pair: wrapped substrings
 * render inside a real <s> element, surrounding substrings render as
 * plain text in place.
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

  const nodes: ReactNode[] = []
  let lastIndex = 0
  let key = 0

  for (const match of text.matchAll(STRIKETHROUGH_RE)) {
    const start = match.index
    if (start > lastIndex) nodes.push(text.slice(lastIndex, start))
    nodes.push(<s key={key++}>{match[1]}</s>)
    lastIndex = start + match[0].length
  }

  if (nodes.length === 0) return text
  if (lastIndex < text.length) nodes.push(text.slice(lastIndex))
  return <Fragment>{nodes}</Fragment>
}
