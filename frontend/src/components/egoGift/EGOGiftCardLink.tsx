import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftName } from './EGOGiftName'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

interface EGOGiftCardLinkProps {
  /** The EGO gift data to display */
  gift: EGOGiftListItem
  /** Enhancement level (0, 1, or 2) */
  enhancement?: 0 | 1 | 2
  /** Additional CSS classes for the link wrapper */
  className?: string
}

/**
 * Navigation wrapper for EGOGiftCard that links to the EGO gift detail page.
 * Use this when clicking the card should navigate to `/ego-gift/$id`.
 *
 * @example
 * // In a list view with navigation
 * <EGOGiftCardLink gift={gift} />
 *
 * // With enhancement level
 * <EGOGiftCardLink gift={gift} enhancement={1} />
 */
export function EGOGiftCardLink({
  gift,
  enhancement = 0,
  className,
}: EGOGiftCardLinkProps) {
  return (
    <Link
      to="/ego-gift/$id"
      params={{ id: gift.id }}
      className={cn(
        'block transition-all hover:scale-105',
        className
      )}
    >
      <div className="flex flex-col items-center gap-1.5">
        <EGOGiftCard gift={gift} enhancement={enhancement} />
        <span className="text-xs text-center text-foreground line-clamp-2 w-24 leading-tight font-medium">
          <Suspense fallback={<Skeleton className="h-5 w-20 bg-foreground" />}>
            <EGOGiftName id={gift.id} />
          </Suspense>
        </span>
      </div>
    </Link>
  )
}
