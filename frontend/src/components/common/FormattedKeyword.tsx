/**
 * FormattedKeyword - Renders a single resolved keyword with icon, color, and popover
 *
 * Supports three keyword types:
 * - battleKeyword: Icon + colored name + click popover with description
 * - skillTag: Colored display text only (no icon, no popover)
 * - unknown: Plain text with brackets preserved [Key]
 */

import { useState } from 'react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { getBattleKeywordIconPath } from '@/lib/assetPaths'
import { cn } from '@/lib/utils'
import type { ResolvedKeyword } from '@/types/KeywordTypes'

interface FormattedKeywordProps {
  /** Resolved keyword data from useKeywordFormatter */
  keyword: ResolvedKeyword
  /** Additional CSS classes */
  className?: string
}

/**
 * Renders a formatted keyword based on its type.
 *
 * Battle keywords: Icon (if iconId exists) + colored name + click popover
 * Skill tags: Colored display text only
 * Unknown: Plain text with brackets [key]
 */
export function FormattedKeyword({ keyword, className }: FormattedKeywordProps) {
  const { type, key, displayText, description, iconId, color } = keyword
  const [iconError, setIconError] = useState(false)

  // Unknown keywords: render as plain text with brackets
  if (type === 'unknown') {
    return <span className={className}>[{key}]</span>
  }

  // Skill tags: styled text only (no icon, no popover)
  if (type === 'skillTag') {
    return (
      <span
        className={cn('font-medium', className)}
        style={{ color }}
      >
        {displayText}
      </span>
    )
  }

  // Path for the images
  const path = iconId ?? key

  // Battle keywords: icon + colored name + popover
  // Using button for keyboard accessibility (focus, enter/space to open)
  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          className={cn(
            'items-center gap-1 font-medium',
            'cursor-pointer hover:underline transition-opacity',
            'border-0 bg-transparent p-0 m-0',
            className
          )}
          style={{ color }}
        >
          {!iconError && (
            <img
              src={getBattleKeywordIconPath(path)}
              alt=""
              aria-hidden="true"
              className="w-4 h-4 inline-block shrink-0"
              onError={() => setIconError(true)}
            />
          )}
          <span>{displayText}</span>
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-64 max-w-sm">
        <div className="space-y-2">
          {/* Header: keyword name with color */}
          <h4
            className="font-semibold"
            style={{ color }}
          >
            {displayText}
          </h4>
          {/* Body: description */}
          {description && (
            <p className="text-sm text-muted-foreground">
              {description}
            </p>
          )}
        </div>
      </PopoverContent>
    </Popover>
  )
}
