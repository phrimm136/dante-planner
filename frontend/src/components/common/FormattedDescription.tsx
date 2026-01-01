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
import { parseStyleTags } from '@/lib/parseStyleTags'
import { cn } from '@/lib/utils'
import { FormattedKeyword } from './FormattedKeyword'

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
          {/* Format and render each segment in the line */}
          {format(line).map((segment, segmentIndex) => {
            if (segment.type === 'text') {
              // Parse style tags in text segments (for EGO Gift upgrade highlights)
              return <Fragment key={segmentIndex}>{parseStyleTags(segment.content)}</Fragment>
            }

            // Keyword segment - render with FormattedKeyword
            if (segment.keyword) {
              return (
                <FormattedKeyword
                  key={segmentIndex}
                  keyword={segment.keyword}
                />
              )
            }

            // Fallback for malformed segment (should not happen)
            return <Fragment key={segmentIndex}>{parseStyleTags(segment.content)}</Fragment>
          })}
        </Fragment>
      ))}
    </span>
  )
}
