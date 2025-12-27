import { Link } from '@tanstack/react-router'
import type { Identity } from '@/types/IdentityTypes'
import { IdentityCard } from './IdentityCard'
import { cn } from '@/lib/utils'

interface IdentityCardLinkProps {
  /** The identity data to display */
  identity: Identity
  /** Whether the card shows selected state indicator */
  isSelected?: boolean
  /** Additional CSS classes for the link wrapper */
  className?: string
}

/**
 * Navigation wrapper for IdentityCard that links to the identity detail page.
 * Use this when clicking the card should navigate to `/identity/$id`.
 *
 * @example
 * // In a list view with navigation
 * <IdentityCardLink identity={identity} />
 *
 * // With selection state
 * <IdentityCardLink identity={identity} isSelected={true} />
 */
export function IdentityCardLink({
  identity,
  isSelected = false,
  className,
}: IdentityCardLinkProps) {
  return (
    <Link
      to="/identity/$id"
      params={{ id: identity.id }}
      className={cn(
        'block transition-all hover:scale-105',
        className
      )}
    >
      <IdentityCard identity={identity} isSelected={isSelected} />
    </Link>
  )
}
