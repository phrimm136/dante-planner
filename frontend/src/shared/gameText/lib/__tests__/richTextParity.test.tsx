/**
 * Rendering parity across every Unity rich text reader.
 *
 * Each reader renders the shared corpus twice — once through the tokenizer it
 * uses in production, once through the reference parser in richTextOracle —
 * and the markup must be identical.
 */

import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import type { ReactNode } from 'react'

import { ColoredText, parseColorTags } from '../../components/ColoredText'
import { FormattedSanityText } from '../../components/FormattedSanityText'
import { FormattedDescription } from '../../components/FormattedDescription'
import { applyStrikethrough } from '../unityRichText'
import { extractLeadingColor, stripRichTextTags } from '../richText'
import { MALFORMED_SIZE_CORPUS, RICH_TEXT_CORPUS } from './richTextCorpus'
import {
  OracleColoredText,
  OracleFormattedDescription,
  OracleFormattedSanityText,
  oracleApplyStrikethrough,
  oracleExtractLeadingColor,
  oracleParseColorTags,
  oracleStripTags,
} from './richTextOracle'

vi.mock('@/shared/gameText/hooks/useKeywordFormatter', () => ({
  useKeywordFormatter: () => ({
    format: (text: string) => {
      const segments = []
      let lastIndex = 0
      for (const match of text.matchAll(/\[([^\]]+)\]/g)) {
        if (match.index > lastIndex) {
          segments.push({ type: 'text', content: text.slice(lastIndex, match.index) })
        }
        segments.push({
          type: 'keyword',
          content: match[1],
          keyword: {
            type: 'battleKeyword',
            key: match[1],
            displayText: match[1],
            color: '#abcdef',
          },
        })
        lastIndex = match.index + match[0].length
      }
      if (lastIndex < text.length) {
        segments.push({ type: 'text', content: text.slice(lastIndex) })
      }
      return segments
    },
  }),
}))

vi.mock('@/shared/gameText/components/FormattedKeyword', () => ({
  FormattedKeyword: ({
    keyword,
  }: {
    keyword: { key: string; displayText: string; color: string }
  }) => (
    <span data-testid={`keyword-${keyword.key}`} style={{ color: keyword.color }}>
      {keyword.displayText}
    </span>
  ),
}))

function htmlOf(node: ReactNode): string {
  const { container } = render(<span>{node}</span>)
  return (container.firstChild as HTMLElement).innerHTML
}

/** Plain strings stay strings; anything else is compared as rendered markup */
function strikethroughShape(value: ReactNode) {
  return typeof value === 'string' ? { text: value } : { html: htmlOf(value) }
}

/** Corpus entries free of size tags, which parseColorTags now renders itself */
const SIZELESS_CORPUS = RICH_TEXT_CORPUS.filter((entry) => !entry.includes('size'))

describe('parseColorTags parity', () => {
  it.each(SIZELESS_CORPUS)('matches the reference parser for %j', (entry) => {
    expect(parseColorTags(entry)).toHaveLength(oracleParseColorTags(entry).length)
    expect(htmlOf(parseColorTags(entry))).toBe(htmlOf(oracleParseColorTags(entry)))
  })
})

describe('parseColorTags size tags', () => {
  it('renders a size element instead of leaking a literal tag', () => {
    expect(htmlOf(parseColorTags('<size=75%>small text</size>'))).toBe(
      '<span class="text-[75%]">small text</span>',
    )
  })
})

describe('ColoredText parity', () => {
  it.each(RICH_TEXT_CORPUS)('matches the reference parser for %j', (entry) => {
    const actual = render(<ColoredText text={entry} />).container.innerHTML
    const expected = render(<OracleColoredText text={entry} />).container.innerHTML

    expect(actual).toBe(expected)
  })
})

describe('ColoredText malformed size tags', () => {
  const expectedHtml: Record<string, string> = {
    '<size=75%>x': '&lt;size=75%&gt;x',
    '</size>orphan': '&lt;/size&gt;orphan',
    '<size=1><size=2>x</size></size>':
      '<span class="text-[75%]">&lt;size=2&gt;x</span>&lt;/size&gt;',
  }

  it.each(MALFORMED_SIZE_CORPUS)('keeps the unmatched size tag as text for %j', (entry) => {
    const { container } = render(<ColoredText text={entry} />)

    expect(container.innerHTML).toBe(expectedHtml[entry])
  })
})

describe('FormattedSanityText parity', () => {
  it.each(RICH_TEXT_CORPUS)('matches the reference parser for %j', (entry) => {
    const actual = render(<FormattedSanityText text={entry} className="sanity" />).container
      .innerHTML
    const expected = render(<OracleFormattedSanityText text={entry} className="sanity" />).container
      .innerHTML

    expect(actual).toBe(expected)
  })
})

describe('FormattedDescription parity', () => {
  it.each(RICH_TEXT_CORPUS)('matches the reference parser for %j', (entry) => {
    const actual = render(<FormattedDescription text={entry} className="desc" />).container
      .innerHTML
    const expected = render(<OracleFormattedDescription text={entry} className="desc" />).container
      .innerHTML

    expect(actual).toBe(expected)
  })
})

describe('applyStrikethrough parity', () => {
  it.each(RICH_TEXT_CORPUS)('matches the reference parser for %j', (entry) => {
    expect(strikethroughShape(applyStrikethrough(entry))).toEqual(
      strikethroughShape(oracleApplyStrikethrough(entry)),
    )
  })

  it('returns the input string when there is no pair to render', () => {
    const text = 'no strikethrough here'

    expect(applyStrikethrough(text)).toBe(text)
    expect(applyStrikethrough('<s>unclosed')).toBe('<s>unclosed')
  })
})

describe('extractLeadingColor parity', () => {
  it.each(RICH_TEXT_CORPUS)('matches the reference parser for %j', (entry) => {
    expect(extractLeadingColor(entry)).toEqual(oracleExtractLeadingColor(entry))
    expect(stripRichTextTags(entry)).toBe(oracleStripTags(entry))
  })
})
