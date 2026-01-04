import type { ReactNode } from 'react'
import { Link } from '@tanstack/react-router'
import type { IdentityListItem } from '@/types/IdentityTypes'
import { IdentityCard } from './IdentityCard'

interface IdentityCardLinkProps {
  /** The identity data to display */
  identity: IdentityListItem
  /** Custom overlay content (e.g., selected indicator) */
  overlay?: ReactNode
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
 * // With selection overlay
 * <IdentityCardLink identity={identity} overlay={<SelectedIndicator />} />
 */
export function IdentityCardLink({
  identity,
  overlay,
  className,
}: IdentityCardLinkProps) {
  return (
    <Link
      to="/identity/$id"
      params={{ id: identity.id }}
      className={className}
    >
      <IdentityCard identity={identity} overlay={overlay} />
    </Link>
  )
}
