/**
 * Trait badges render Unity rich text the same way the reference parser does:
 * the first color tag colors the whole badge, remaining tags are flattened,
 * and <s> pairs become real strikethrough elements.
 */

import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { Fragment, type ReactNode } from 'react'

import { TraitsI18n } from '../TraitsI18n'

vi.mock('@/shared/filter/hooks/useUnitKeywords', () => ({
  useUnitKeywords: vi.fn(),
}))

import { useUnitKeywords } from '@/shared/filter'

/** Shipped unitKeywords.json values plus the tag shapes that separate parsers */
const LABEL_CORPUS = [
  '<color=#d40000><s>Jia Family</s></color>',
  '<s>The Fingers</s>',
  'サ<size=50%>ル</size>党派',
  'plain label',
  '<color=red>x</color>',
  '<color=>x</color>',
  '<color=#fff>x</color>',
  '<color=#aa0000>a<color=#bb0000>b</color>c</color>',
  '<color=#ff0000>x',
]

function referenceStrikethrough(text: string): ReactNode {
  if (!text.includes('<s>')) return text

  const nodes: ReactNode[] = []
  let lastIndex = 0
  let key = 0

  for (const match of text.matchAll(/<s>([\s\S]*?)<\/s>/g)) {
    if (match.index > lastIndex) nodes.push(text.slice(lastIndex, match.index))
    nodes.push(<s key={key++}>{match[1]}</s>)
    lastIndex = match.index + match[0].length
  }

  if (nodes.length === 0) return text
  if (lastIndex < text.length) nodes.push(text.slice(lastIndex))
  return <Fragment>{nodes}</Fragment>
}

function referenceLabel(label: string): ReactNode {
  const colorMatch = label.match(/<color=([^>]+)>/)
  if (!colorMatch) return referenceStrikethrough(label)

  const color = colorMatch[1]
  const text = label.replace(/<color=[^>]+>/g, '').replace(/<\/color>/g, '')
  return <span style={{ color }}>{referenceStrikethrough(text)}</span>
}

function htmlOf(node: ReactNode): string {
  const { container } = render(<span>{node}</span>)
  return (container.firstChild as HTMLElement).innerHTML
}

describe('TraitsI18n rich text parity', () => {
  it.each(LABEL_CORPUS)('renders %j like the reference parser', (label) => {
    vi.mocked(useUnitKeywords).mockReturnValue({ TRAIT: label })

    const { container } = render(<TraitsI18n traits={['TRAIT']} />)
    const badge = container.querySelector('span.px-2') as HTMLElement

    expect(badge.innerHTML).toBe(htmlOf(referenceLabel(label)))
  })
})
