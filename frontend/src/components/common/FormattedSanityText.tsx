/**
 * FormattedSanityText - Renders sanity condition text with proper formatting
 *
 * Parses Unity-style tags and converts them to React elements:
 * - <size=95%>...</size> → smaller text
 * - <color=red>...</color> → colored text
 * - \n → line breaks
 */

import { Fragment } from 'react'

interface TextSegment {
  type: 'text' | 'size' | 'color' | 'br'
  content: string
  color?: string
}

/**
 * Parse Unity-style tags into segments for safe React rendering
 */
function parseSegments(text: string): TextSegment[] {
  const segments: TextSegment[] = []
  let remaining = text

  while (remaining.length > 0) {
    // Check for newline
    if (remaining.startsWith('\n')) {
      segments.push({ type: 'br', content: '' })
      remaining = remaining.slice(1)
      continue
    }

    // Check for <size=...>
    const sizeMatch = remaining.match(/^<size=[^>]*>([\s\S]*?)<\/size>/)
    if (sizeMatch) {
      // Recursively parse content inside size tag
      const innerSegments = parseSegments(sizeMatch[1])
      innerSegments.forEach(seg => {
        segments.push({ ...seg, type: seg.type === 'text' ? 'size' : seg.type })
      })
      remaining = remaining.slice(sizeMatch[0].length)
      continue
    }

    // Check for <color=...>
    const colorMatch = remaining.match(/^<color=([^>]*)>([\s\S]*?)<\/color>/)
    if (colorMatch) {
      segments.push({ type: 'color', content: colorMatch[2], color: colorMatch[1] })
      remaining = remaining.slice(colorMatch[0].length)
      continue
    }

    // Find next tag or newline
    const nextTagIndex = remaining.search(/<(size|color)=|<\/|[\n]/)
    if (nextTagIndex === -1) {
      // No more tags, add rest as text
      segments.push({ type: 'text', content: remaining })
      break
    } else if (nextTagIndex === 0) {
      // Closing tag without opener (shouldn't happen), skip it
      const closeMatch = remaining.match(/^<\/[^>]*>/)
      if (closeMatch) {
        remaining = remaining.slice(closeMatch[0].length)
      } else {
        segments.push({ type: 'text', content: remaining[0] })
        remaining = remaining.slice(1)
      }
    } else {
      // Add text before next tag
      segments.push({ type: 'text', content: remaining.slice(0, nextTagIndex) })
      remaining = remaining.slice(nextTagIndex)
    }
  }

  return segments
}

interface FormattedSanityTextProps {
  text: string
  className?: string
}

/**
 * Renders sanity condition text with size/color formatting preserved
 */
export function FormattedSanityText({ text, className }: FormattedSanityTextProps) {
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
