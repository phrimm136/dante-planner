/**
 * FormattedDescription - Parses and renders skill/passive descriptions with formatted keywords
 *
 * Wraps useKeywordFormatter hook to transform raw description text with [BracketedKeywords]
 * into styled, interactive content. Also handles <style="upgradeHighlight"> tags for EGO Gifts.
 * Handles newlines and consecutive keywords.
 *
 * IMPORTANT: Must be wrapped in Suspense boundary as it uses useSuspenseQuery internally.
 */

import { Fragment } from 'react'
import { useKeywordFormatter } from '@/hooks/useKeywordFormatter'
import { cn } from '@/lib/utils'
import { FormattedKeyword } from './FormattedKeyword'

const UPGRADE_HIGHLIGHT_COLOR = '#f8c200'

/** Represents a segment after style tag parsing */
interface StyleSegment {
  content: string
  isHighlighted: boolean
}

/**
 * Parse style tags FIRST, before keyword formatting.
 * This preserves style boundaries even when keywords are inside styled regions.
 */
function parseStyleSegments(text: string): StyleSegment[] {
  const segments: StyleSegment[] = []
  const styleRegex = /<style="upgradeHighlight">([\s\S]*?)<\/style>/g
  let lastIndex = 0
  let match

  while ((match = styleRegex.exec(text)) !== null) {
    // Add unstyled text before the match
    if (match.index > lastIndex) {
      segments.push({ content: text.slice(lastIndex, match.index), isHighlighted: false })
    }
    // Add the styled content (without tags)
    segments.push({ content: match[1], isHighlighted: true })
    lastIndex = match.index + match[0].length
  }

  // Add remaining unstyled text
  if (lastIndex < text.length) {
    segments.push({ content: text.slice(lastIndex), isHighlighted: false })
  }

  return segments
}

interface FormattedDescriptionProps {
  /** Raw description text with [BracketedKeywords] */
  text: string
  /** Additional CSS classes */
  className?: string
}

/**
 * Renders a description with formatted keywords.
 *
 * Parses [BracketedKeywords] and renders them as styled, interactive elements:
 * - Battle keywords: Icon + colored text + click popover
 * - Skill tags: Colored text
 * - Unknown: Plain text with brackets preserved
 *
 * Also parses <style="upgradeHighlight"> tags for EGO Gift descriptions.
 * Style tags are parsed FIRST to preserve boundaries when keywords are inside styled regions.
 * Handles newlines by converting \n to <br /> elements.
 *
 * @example
 * ```tsx
 * <Suspense fallback={<LoadingState />}>
 *   <FormattedDescription text="Apply 2 [Sinking] [OnSucceedAttack]" />
 * </Suspense>
 * ```
 */
export function FormattedDescription({ text, className }: FormattedDescriptionProps) {
  const { format } = useKeywordFormatter()

  // Handle empty string
  if (!text) {
    return null
  }

  // Split text by newlines first, then format each line
  const lines = text.split('\n')

  return (
    <span className={cn('inline', className)}>
      {lines.map((line, lineIndex) => (
        <Fragment key={lineIndex}>
          {/* Add line break before all lines except the first */}
          {lineIndex > 0 && <br />}
          {/* Parse style tags FIRST, then format keywords within each segment */}
          {parseStyleSegments(line).map((styleSegment, styleIndex) => {
            // Format keywords within this style segment
            const keywordSegments = format(styleSegment.content)

            // Render keyword segments - keywords keep their own color, text gets highlight
            const content = keywordSegments.map((segment, segmentIndex) => {
              if (segment.type === 'text') {
                // Plain text - apply highlight color if in highlighted segment
                if (styleSegment.isHighlighted) {
                  return (
                    <span key={segmentIndex} style={{ color: UPGRADE_HIGHLIGHT_COLOR }}>
                      {segment.content}
                    </span>
                  )
                }
                return <Fragment key={segmentIndex}>{segment.content}</Fragment>
              }

              // Keyword segment - render with FormattedKeyword (keeps its own color)
              if (segment.keyword) {
                return (
                  <FormattedKeyword
                    key={segmentIndex}
                    keyword={segment.keyword}
                  />
                )
              }

              // Fallback for malformed segment
              return <Fragment key={segmentIndex}>{segment.content}</Fragment>
            })

            // No wrapper needed - text segments already have highlight color applied
            return <Fragment key={styleIndex}>{content}</Fragment>
          })}
        </Fragment>
      ))}
    </span>
  )
}
