/**
 * Parses style tags in EGO gift descriptions and converts to React elements
 * Handles <style="upgradeHighlight">content</style> tags with #f8c200 color
 */

const UPGRADE_HIGHLIGHT_COLOR = '#f8c200'

/**
 * Parses style tags and converts to React elements
 * @param text - Text with <style="upgradeHighlight">content</style> tags
 * @returns Array of React elements with styled spans
 */
export function parseStyleTags(text: string): React.ReactNode[] {
  const parts: React.ReactNode[] = []
  const styleRegex = /<style="upgradeHighlight">([\s\S]*?)<\/style>/g
  let lastIndex = 0
  let match

  while ((match = styleRegex.exec(text)) !== null) {
    // Add text before the match
    if (match.index > lastIndex) {
      parts.push(text.slice(lastIndex, match.index))
    }

    // Add the styled span
    const [, content] = match
    parts.push(
      <span key={match.index} style={{ color: UPGRADE_HIGHLIGHT_COLOR }}>
        {content}
      </span>
    )

    lastIndex = match.index + match[0].length
  }

  // Add remaining text
  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex))
  }

  return parts
}

/**
 * Formats description text with style tag parsing and line breaks
 * @param text - Description text with potential style tags
 * @returns React node with styled content and line breaks
 */
export function formatGiftDescription(text: string): React.ReactNode {
  // Split by newlines first, then parse style tags for each line
  const lines = text.split('\n')

  return lines.map((line, lineIndex) => (
    <span key={lineIndex}>
      {parseStyleTags(line)}
      {lineIndex < lines.length - 1 && <br />}
    </span>
  ))
}
