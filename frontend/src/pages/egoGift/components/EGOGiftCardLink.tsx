import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'
import { Skeleton } from '@/components/ui/skeleton'
import type { EGOGiftEntity } from '../types/EGOGiftTypes'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftName } from './EGOGiftName'
import { cn } from '@/lib/utils'

interface EGOGiftCardLinkProps {
  gift: EGOGiftEntity
  enhancement?: 0 | 1 | 2
  className?: string
}

export const EGOGiftCardLink = function EGOGiftCardLink({
  gift,
  enhancement = 0,
  className,
}: EGOGiftCardLinkProps) {
  return (
    <Link to="/ego-gift/$id" params={{ id: gift.id }} className={cn('block w-full', className)}>
      <div className="flex flex-col items-center gap-1.5">
        <EGOGiftCard gift={gift} enhancement={enhancement} enableHoverHighlight />
        <span className="text-xs text-center text-foreground line-clamp-2 w-full leading-tight font-medium">
          <Suspense fallback={<Skeleton className="h-5 w-full" />}>
            <EGOGiftName id={gift.id} />
          </Suspense>
        </span>
      </div>
    </Link>
  )
}
