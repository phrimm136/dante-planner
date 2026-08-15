import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import colorCode from '@static/data/colorCode.json'
import { KeywordCard } from './KeywordCard'
import { KeywordName } from './KeywordName'

const colorMap = colorCode as Record<string, string>

interface KeywordCardLinkProps {
  id: string
  iconId: string | null
  buffType: string
  className?: string
}

/**
 * Navigation wrapper for KeywordCard that links to the keyword detail page.
 * Renders card + colored name below.
 *
 * Pattern Source: EGOGiftCardLink.tsx
 * Memoized by id to prevent re-renders during list filtering.
 */
export const KeywordCardLink = function KeywordCardLink({
  id,
  iconId,
  buffType,
  className,
}: KeywordCardLinkProps) {
  const nameColor = colorMap[buffType] ?? colorMap['Neutral']

  return (
    <Link to="/keyword/$id" params={{ id }} className={cn(className)}>
      <div className="flex flex-col items-center gap-1.5">
        <KeywordCard id={id} iconId={iconId} enableHoverHighlight />
        <span
          className="text-xs text-center line-clamp-2 w-24 leading-tight font-medium"
          style={{ color: nameColor }}
        >
          <Suspense fallback={<Skeleton className="h-5 w-24" />}>
            <KeywordName id={id} />
          </Suspense>
        </span>
      </div>
    </Link>
  )
}
