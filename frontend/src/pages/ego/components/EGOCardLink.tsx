import { Link } from '@tanstack/react-router'
import type { EGOEntity } from '../types/EGOTypes'
import { EGOCard } from './EGOCard'
import { cn } from '@/lib/utils'

interface EGOCardLinkProps {
  ego: EGOEntity
  className?: string
}

export const EGOCardLink = function EGOCardLink({ ego, className }: EGOCardLinkProps) {
  return (
    <Link to="/ego/$id" params={{ id: ego.id }} className={cn('group block transition', className)}>
      <EGOCard ego={ego} />
    </Link>
  )
}
