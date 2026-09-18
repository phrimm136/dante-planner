import { useState } from 'react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { getBattleKeywordIconPath } from '@/shared/assets'
import { applyStrikethrough } from '../lib/unityRichText'
import { cn } from '@/lib/utils'
import type { ResolvedKeyword } from '../types/KeywordTypes'
import { FLAVOR_TEXT_COLOR, SECTION_STYLES } from '@/lib/constants'

interface FormattedKeywordProps {
  keyword: ResolvedKeyword
  className?: string | undefined
}

export function FormattedKeyword({ keyword, className }: FormattedKeywordProps) {
  const { type, key, displayText, description, flavor, iconId, color } = keyword
  const [_iconError, _setIconError] = useState(false)

  if (type === 'unknown') {
    return <span className={className}>[{key}]</span>
  }

  if (type === 'skillTag') {
    return (
      <span className={cn('font-medium', className)} style={{ color }}>
        {applyStrikethrough(displayText)}
      </span>
    )
  }

  const path = iconId ?? key

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation()
          }}
          className={cn(
            'items-center gap-1 font-medium',
            'cursor-pointer underline transition-opacity',
            'border-0 bg-transparent p-0 m-0',
            className,
          )}
          style={{ color }}
        >
          <img
            src={getBattleKeywordIconPath(path)}
            alt=""
            aria-hidden="true"
            className="w-4 h-4 inline-block shrink-0"
          />
          <span>{applyStrikethrough(displayText)}</span>
        </button>
      </PopoverTrigger>
      <PopoverContent
        sideOffset={4}
        className={cn(
          'z-50 outline-hidden',
          'w-auto max-w-md bg-black/85 border-neutral-800',
          '!animate-none data-[state=open]:!animate-none data-[state=closed]:!animate-none text-foreground rounded-none p-2',
        )}
      >
        <div className="space-y-1 max-w-[280px]">
          <div className={SECTION_STYLES.LAYOUT.rowTight}>
            <img
              src={getBattleKeywordIconPath(path)}
              alt=""
              aria-hidden="true"
              className="w-6 h-6 shrink-0"
            />
            <h4 className="font-bold text-lg">{applyStrikethrough(displayText)}</h4>
          </div>
          {description && (
            <p className="text-sm whitespace-pre-line px-2">{applyStrikethrough(description)}</p>
          )}
          {flavor && (
            <p
              data-testid="keyword-flavor"
              className="text-xs italic whitespace-pre-line px-2"
              style={{ color: FLAVOR_TEXT_COLOR }}
            >
              {applyStrikethrough(flavor)}
            </p>
          )}
        </div>
      </PopoverContent>
    </Popover>
  )
}
