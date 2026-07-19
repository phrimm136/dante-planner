import { getAbEventImagePath } from '@/shared/assets'
import { cn } from '@/lib/utils'

interface AbEventCardProps {
  eventId: string
  hasImage: boolean
  illustId?: string
  enableHoverHighlight?: boolean
  className?: string
}

/**
 * Pure view component for rendering an abnormality event card.
 * Wide landscape image.
 */
export function AbEventCard({
  eventId,
  hasImage,
  illustId,
  enableHoverHighlight = false,
  className,
}: AbEventCardProps) {
  return (
    <div className={cn('group relative flex flex-col', className)}>
      <div className="relative w-full aspect-[3/2] rounded-sm overflow-hidden bg-muted">
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

        {enableHoverHighlight && (
          <div className="absolute inset-0 bg-white/10 opacity-0 group-hover:opacity-100 transition-opacity" />
        )}
      </div>
    </div>
  )
}
