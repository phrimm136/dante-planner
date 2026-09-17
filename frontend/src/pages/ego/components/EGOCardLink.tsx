import { Link } from '@tanstack/react-router'
import type { EGOEntity } from '../types/EGOTypes'
import { EGOCard } from './EGOCard'
import { cn } from '@/lib/utils'

interface EGOCardLinkProps {
  /** The EGO data to display */
  ego: EGOEntity
  /** Additional CSS classes for the link wrapper */
  className?: string
}

/**
 * Navigation wrapper for EGOCard that links to the EGO detail page.
 * Use this when clicking the card should navigate to `/ego/$id`.
 *
 * @example
 * // In a list view with navigation
 * <EGOCardLink ego={ego} />
 */
export const EGOCardLink = function EGOCardLink({ ego, className }: EGOCardLinkProps) {
  return (
    <Link to="/ego/$id" params={{ id: ego.id }} className={cn('group block transition', className)}>
      <EGOCard ego={ego} />
    </Link>
  )
}
