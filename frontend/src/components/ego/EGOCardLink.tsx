import { memo, useState } from 'react'
import { Link } from '@tanstack/react-router'
import type { EGOListItem } from '@/types/EGOTypes'
import { EGOCard } from './EGOCard'
import { cn } from '@/lib/utils'

interface EGOCardLinkProps {
  /** The EGO data to display */
  ego: EGOListItem
  /** Additional CSS classes for the link wrapper */
  className?: string
}

/**
 * Navigation wrapper for EGOCard that links to the EGO detail page.
 * Use this when clicking the card should navigate to `/ego/$id`.
 *
 * Memoized by ego.id to prevent re-renders during list filtering.
 *
 * @example
 * // In a list view with navigation
 * <EGOCardLink ego={ego} />
 */
export const EGOCardLink = memo(function EGOCardLink({
  ego,
  className,
}: EGOCardLinkProps) {
  const [isHovered, setIsHovered] = useState(false)

  return (
    <Link
      to="/ego/$id"
      params={{ id: ego.id }}
      className={cn(
        'block transition-all',
        className
      )}
      onMouseEnter={() => { setIsHovered(true) }}
      onMouseLeave={() => { setIsHovered(false) }}
    >
      <EGOCard ego={ego} isHighlighted={isHovered} />
    </Link>
  )
}, (prev, next) => prev.ego.id === next.ego.id && prev.className === next.className)
