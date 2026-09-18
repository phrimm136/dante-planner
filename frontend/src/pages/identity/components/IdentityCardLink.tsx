import { type ReactNode } from 'react'
import { Link } from '@tanstack/react-router'
import type { IdentityEntity } from '../types/IdentityTypes'
import { cn } from '@/lib/utils'
import { IdentityCard } from './IdentityCard'

interface IdentityCardLinkProps {
  identity: IdentityEntity
  overlay?: ReactNode
  className?: string
}

export const IdentityCardLink = function IdentityCardLink({
  identity,
  overlay,
  className,
}: IdentityCardLinkProps) {
  return (
    <Link
      to="/identity/$id"
      params={{ id: identity.id }}
      className={cn('group block transition-all', className)}
    >
      <IdentityCard identity={identity} overlay={overlay} />
    </Link>
  )
}
