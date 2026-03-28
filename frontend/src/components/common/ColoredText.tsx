/**
 * ColoredText — Unified Unity rich text tag processing
 *
 * Single source of truth for parsing Unity color/size tags into React nodes.
 * Handles: nested tags, malformed close tags, size tags.
 *
 * Used by: AbEventDetailPage, ThemePackCard, formatBuffDescription, etc.
 */

/**
 * Sanitize Unity rich text before parsing:
 * - <size=N%>content</size> -> <small>content</small>
 * - </color=#hex> -> </color> (malformed close tags)
 */
function sanitize(text: string): string {
  return text
    .replace(/<size=[^>]*>/g, '<small>')
    .replace(/<\/size>/g, '</small>')
    .replace(/<\/color=[^>]*>/g, '</color>')
}

/**
 * Parse Unity color tags into React nodes. Handles nested tags recursively.
 *
 * This is the single consolidated tag processing function.
 * All color tag parsing in the codebase should use this.
 */
export function parseColorTags(text: string): React.ReactNode[] {
  const sanitized = sanitize(text)
  return parseRecursive(sanitized, 0)
}

function parseRecursive(text: string, keyBase: number): React.ReactNode[] {
  const parts: React.ReactNode[] = []
  const openTagRegex = /<color=(#[0-9a-fA-F]{6})>/
  let lastIndex = 0

  while (lastIndex < text.length) {
    const slice = text.slice(lastIndex)
    const match = openTagRegex.exec(slice)
    if (!match) {
      parts.push(slice)
      break
    }

    const tagStart = lastIndex + match.index
    const color = match[1]
    const contentStart = tagStart + match[0].length

    if (tagStart > lastIndex) {
      parts.push(text.slice(lastIndex, tagStart))
    }

    // Find matching </color> using depth counter for nesting
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
        </span>
      )
    }

    lastIndex = pos + '</color>'.length
  }

  return parts
}

/** Strip all color tags, keeping inner text */
export function stripColorTags(text: string): string {
  // Repeatedly strip until no tags remain (handles nested)
  let result = text
  let prev = ''
  while (result !== prev) {
    prev = result
    result = result.replace(/<color=#[0-9a-fA-F]{6}>([^<]*)<\/color>/g, '$1')
  }
  return result
}

/**
 * ColoredText component — renders Unity rich text as JSX.
 * Handles <color>, <size>, nested tags, and malformed close tags.
 */
export function ColoredText({ text }: { text: string }) {
  const sanitized = sanitize(text)

  // Handle <small> tags (from <size> conversion) by splitting first
  const smallRegex = /<small>([\s\S]*?)<\/small>/g
  const topParts: React.ReactNode[] = []
  let topLastIndex = 0
  let smallMatch

  while ((smallMatch = smallRegex.exec(sanitized)) !== null) {
    if (smallMatch.index > topLastIndex) {
      topParts.push(...parseRecursive(sanitized.slice(topLastIndex, smallMatch.index), topLastIndex))
    }
    topParts.push(
      <span key={`small-${smallMatch.index}`} className="text-[75%]">
        {parseRecursive(smallMatch[1], smallMatch.index)}
      </span>
    )
    topLastIndex = smallRegex.lastIndex
  }

  if (topLastIndex < sanitized.length) {
    topParts.push(...parseRecursive(sanitized.slice(topLastIndex), topLastIndex))
  }

  return <>{topParts}</>
}
