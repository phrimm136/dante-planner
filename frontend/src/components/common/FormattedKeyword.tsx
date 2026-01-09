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
  // Icon error state kept for future use when icon loading is implemented
  const [_iconError, _setIconError] = useState(false)

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
            'cursor-pointer underline transition-opacity',
            'border-0 bg-transparent p-0 m-0',
            className
          )}
          style={{ color }}
        >
          <img
            src={getBattleKeywordIconPath(path)}
            alt=""
            aria-hidden="true"
            className="w-4 h-4 inline-block shrink-0"
          />
          <span>{displayText}</span>
        </button>
      </PopoverTrigger>
      <PopoverContent
        className={cn(
          'w-auto max-w-md bg-black/85 border-neutral-800',
          '!animate-none data-[state=open]:!animate-none data-[state=closed]:!animate-none text-foreground rounded-none p-2'
        )}
      >
        <div className="space-y-1 max-w-[280px]">
          <div className="flex items-center gap-1">
            <img
              src={getBattleKeywordIconPath(path)}
              alt=""
              aria-hidden="true"
              className="w-6 h-6 shrink-0"
            />
            <h4 className="font-bold text-lg">
              {displayText}
            </h4>
          </div>
          {description && (
            <p className="text-sm whitespace-pre-line px-2">
              {description}
            </p>
          )}
        </div>
      </PopoverContent>
    </Popover>
  )
}
