import { Suspense } from 'react'
import { getAbEventImagePath } from '@/shared/assets'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { AbEventDesc } from './AbEventDesc'

interface AbEventCardProps {
  eventId: string
  hasImage: boolean
  illustId?: string | undefined
  enableHoverHighlight?: boolean
  className?: string
}

/**
 * Pure view component for rendering an abnormality event card.
 * Wide landscape image with a clamped description below.
 */
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
          enableHoverHighlight && 'selectable [--selectable-transition-duration:0ms]',
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
