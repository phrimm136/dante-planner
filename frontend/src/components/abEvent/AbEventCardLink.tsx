import { memo } from 'react'
import { Link } from '@tanstack/react-router'
import { AbEventCard } from './AbEventCard'
import { cn } from '@/lib/utils'

interface AbEventCardLinkProps {
  eventId: string
  hasImage: boolean
  className?: string
}

/**
 * Navigation wrapper for AbEventCard that links to the ab-event detail page.
 * Memoized by eventId to prevent re-renders during list filtering.
 */
export const AbEventCardLink = memo(function AbEventCardLink({
  eventId,
  hasImage,
  className,
}: AbEventCardLinkProps) {
  return (
    <Link
      to="/ab-event/$id"
      params={{ id: eventId }}
      className={cn(className)}
    >
      <AbEventCard
        eventId={eventId}
        hasImage={hasImage}
        enableHoverHighlight
      />
    </Link>
  )
}, (prev, next) => prev.eventId === next.eventId)
