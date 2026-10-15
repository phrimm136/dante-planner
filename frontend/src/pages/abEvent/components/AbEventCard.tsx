import { Suspense } from 'react'
import { getAbEventImagePath } from '@/shared/assets'
import { Skeleton } from '@/components/ui/skeleton'
import { LIST_CARD_HOVER_CLASS } from '@/lib/constants'
import { cn } from '@/lib/utils'
import { AbEventDesc } from './AbEventDesc'

interface AbEventCardProps {
  eventId: string
  hasImage: boolean
  illustId?: string | undefined
  enableHoverHighlight?: boolean
  className?: string
}

export function AbEventCard({
  eventId,
  hasImage,
  illustId,
  enableHoverHighlight = false,
  className,
}: AbEventCardProps) {
  return (
    <div className={cn('group relative flex flex-col gap-1.5', className)}>
      <div
        className={cn(
          'relative w-full aspect-[3/2] rounded-sm overflow-hidden bg-muted',
          enableHoverHighlight && LIST_CARD_HOVER_CLASS,
        )}
      >
        {hasImage || illustId ? (
          <img
            src={getAbEventImagePath(illustId ?? eventId)}
            alt=""
            loading="lazy"
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-muted-foreground text-xs">
            {eventId}
          </div>
        )}
      </div>

      <div className="text-xs leading-tight text-muted-foreground line-clamp-2 px-1">
        <Suspense fallback={<Skeleton className="h-8 w-full" />}>
          <AbEventDesc id={eventId} />
        </Suspense>
      </div>
    </div>
  )
}
