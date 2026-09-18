import { Link } from '@tanstack/react-router'
import { AbEventCard } from './AbEventCard'
import { cn } from '@/lib/utils'

interface AbEventCardLinkProps {
  eventId: string
  hasImage: boolean
  illustId?: string | undefined
  className?: string
}

export const AbEventCardLink = function AbEventCardLink({
  eventId,
  hasImage,
  illustId,
  className,
}: AbEventCardLinkProps) {
  return (
    <Link to="/ab-event/$id" params={{ id: eventId }} className={cn(className)}>
      <AbEventCard eventId={eventId} hasImage={hasImage} illustId={illustId} enableHoverHighlight />
    </Link>
  )
}
