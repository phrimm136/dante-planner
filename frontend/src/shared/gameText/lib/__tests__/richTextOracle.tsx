/**
 * Reference readers for the Unity rich text corpus.
 *
 * Each function here is an independent hand-rolled parser for one consumer,
 * kept as the parity oracle: the tokenizer-backed readers must render exactly
 * what these render for every corpus entry.
 */

import { Fragment, type ReactNode } from 'react'

import { cn } from '@/lib/utils'
import { useKeywordFormatter } from '../../hooks/useKeywordFormatter'
import { FormattedKeyword } from '../../components/FormattedKeyword'

const SMALL_RE = /<small>([\s\S]*?)<\/small>/g
const STRIKETHROUGH_RE = /<s>([\s\S]*?)<\/s>/g
const STYLE_RE = /<style="upgradeHighlight">([\s\S]*?)<\/style>/g
const UPGRADE_HIGHLIGHT_COLOR = '#f8c200'

function sanitize(text: string): string {
  return text
    .replace(/<size=[^>]*>/g, '<small>')
    .replace(/<\/size>/g, '</small>')
    .replace(/<\/color=[^>]*>/g, '</color>')
}

function parseRecursive(text: string, keyBase: number): ReactNode[] {
  const parts: ReactNode[] = []
  const openTagRegex = /<color=(#[0-9a-fA-F]{6})>/
  let lastIndex = 0

  while (lastIndex < text.length) {
    const slice = text.slice(lastIndex)
    const match = slice.match(openTagRegex)
    if (!match || match.index === undefined) {
      parts.push(slice)
      break
    }

    const tagStart = lastIndex + match.index
    const color = match[1]
    const contentStart = tagStart + match[0].length

    if (tagStart > lastIndex) {
      parts.push(text.slice(lastIndex, tagStart))
    }

    let depth = 1
    let pos = contentStart
    while (pos < text.length && depth > 0) {
      if (text.startsWith('<color=', pos)) {
        depth++
        const gtIndex = text.indexOf('>', pos)
        pos = gtIndex === -1 ? text.length : gtIndex + 1
      } else if (text.startsWith('</color>', pos)) {
        depth--
        if (depth > 0) pos += '</color>'.length
      } else {
        pos++
      }
    }

    const innerContent = text.slice(contentStart, pos)
    if (innerContent) {
      parts.push(
        <span key={keyBase + tagStart} style={{ color }}>
          {parseRecursive(innerContent, keyBase + contentStart)}
        </span>,
      )
    }

    lastIndex = pos + '</color>'.length
  }

  return parts
}

export function oracleParseColorTags(text: string): ReactNode[] {
  return parseRecursive(sanitize(text), 0)
}

export function OracleColoredText({ text }: { text: string }) {
  const sanitized = sanitize(text)
  const topParts: ReactNode[] = []
  let topLastIndex = 0

  for (const smallMatch of sanitized.matchAll(SMALL_RE)) {
    const start = smallMatch.index
    if (start > topLastIndex) {
      topParts.push(...parseRecursive(sanitized.slice(topLastIndex, start), topLastIndex))
    }
    topParts.push(
      <span key={`small-${start}`} className="text-[75%]">
        {parseRecursive(smallMatch[1], start)}
      </span>,
    )
    topLastIndex = start + smallMatch[0].length
  }

  if (topLastIndex < sanitized.length) {
    topParts.push(...parseRecursive(sanitized.slice(topLastIndex), topLastIndex))
  }

  return <>{topParts}</>
}

interface TextSegment {
  type: 'text' | 'size' | 'color' | 'br'
  content: string
  color?: string
}

function parseSegments(text: string): TextSegment[] {
  const segments: TextSegment[] = []
  let remaining = text

  while (remaining.length > 0) {
    if (remaining.startsWith('\n')) {
      segments.push({ type: 'br', content: '' })
      remaining = remaining.slice(1)
      continue
    }

    const sizeMatch = remaining.match(/^<size=[^>]*>([\s\S]*?)<\/size>/)
    if (sizeMatch) {
      const innerSegments = parseSegments(sizeMatch[1])
      innerSegments.forEach((seg) => {
        segments.push({ ...seg, type: seg.type === 'text' ? 'size' : seg.type })
      })
      remaining = remaining.slice(sizeMatch[0].length)
      continue
    }

    const colorMatch = remaining.match(/^<color=([^>]*)>([\s\S]*?)<\/color>/)
    if (colorMatch) {
      segments.push({ type: 'color', content: colorMatch[2], color: colorMatch[1] })
      remaining = remaining.slice(colorMatch[0].length)
      continue
    }

    const nextTagIndex = remaining.search(/<(size|color)=|<\/|[\n]/)
    if (nextTagIndex === -1) {
      segments.push({ type: 'text', content: remaining })
      break
    } else if (nextTagIndex === 0) {
      const closeMatch = remaining.match(/^<\/[^>]*>/)
      if (closeMatch) {
        remaining = remaining.slice(closeMatch[0].length)
      } else {
        segments.push({ type: 'text', content: remaining[0] })
        remaining = remaining.slice(1)
      }
    } else {
      segments.push({ type: 'text', content: remaining.slice(0, nextTagIndex) })
      remaining = remaining.slice(nextTagIndex)
    }
  }

  return segments
}

export function OracleFormattedSanityText({
  text,
  className,
}: {
  text: string
  className?: string
}) {
  if (!text) return null

  const segments = parseSegments(text)

  return (
    <span className={className}>
      {segments.map((segment, idx) => {
        switch (segment.type) {
          case 'br':
            return <br key={idx} />
          case 'size':
            return (
              <span key={idx} className="text-xs">
                {segment.content}
              </span>
            )
          case 'color':
            return (
              <span key={idx} style={{ color: segment.color }}>
                {segment.content}
              </span>
            )
          default:
            return <Fragment key={idx}>{segment.content}</Fragment>
        }
      })}
    </span>
  )
}

interface StyleSegment {
  content: string
  isHighlighted: boolean
}

export function oracleParseStyleSegments(text: string): StyleSegment[] {
  const segments: StyleSegment[] = []
  let lastIndex = 0

  for (const match of text.matchAll(STYLE_RE)) {
    if (match.index > lastIndex) {
      segments.push({ content: text.slice(lastIndex, match.index), isHighlighted: false })
    }
    segments.push({ content: match[1], isHighlighted: true })
    lastIndex = match.index + match[0].length
  }

  if (lastIndex < text.length) {
    segments.push({ content: text.slice(lastIndex), isHighlighted: false })
  }

  return segments
}

export function OracleFormattedDescription({
  text,
  className,
}: {
  text: string
  className?: string
}) {
  const { format } = useKeywordFormatter()

  if (!text) {
    return null
  }

  const lines = text.split('\n')

  return (
    <span className={cn('inline', className)}>
      {lines.map((line, lineIndex) => (
        <Fragment key={lineIndex}>
          {lineIndex > 0 && <br />}
          {oracleParseStyleSegments(line).map((styleSegment, styleIndex) => {
            const keywordSegments = format(styleSegment.content)

            const content = keywordSegments.map((segment, segmentIndex) => {
              if (segment.type === 'text') {
                if (styleSegment.isHighlighted) {
                  return (
                    <span key={segmentIndex} style={{ color: UPGRADE_HIGHLIGHT_COLOR }}>
                      {segment.content}
                    </span>
                  )
                }
                return <Fragment key={segmentIndex}>{segment.content}</Fragment>
              }

              if (segment.keyword) {
                return <FormattedKeyword key={segmentIndex} keyword={segment.keyword} />
              }

              return <Fragment key={segmentIndex}>{segment.content}</Fragment>
            })

            return <Fragment key={styleIndex}>{content}</Fragment>
          })}
        </Fragment>
      ))}
    </span>
  )
}

export function oracleApplyStrikethrough(text: string): ReactNode {
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

export interface OracleLeadingColor {
  color?: string
  text: string
}

export function oracleExtractLeadingColor(input: string): OracleLeadingColor {
  const colorMatch = input.match(/<color=([^>]+)>/)
  if (!colorMatch) return { text: input }

  const color = colorMatch[1]
  const text = input.replace(/<color=[^>]+>/g, '').replace(/<\/color>/g, '')
  return { color, text }
}

export function oracleStripTags(label: string): string {
  return label.replace(/<[^>]+>/g, '')
}

export function oracleFormatUnitKeywordLabel(label: string): ReactNode {
  const colorMatch = label.match(/<color=([^>]+)>/)
  if (!colorMatch) return oracleApplyStrikethrough(label)

  const color = colorMatch[1]
  const text = label.replace(/<color=[^>]+>/g, '').replace(/<\/color>/g, '')
  return <span style={{ color }}>{oracleApplyStrikethrough(text)}</span>
}
