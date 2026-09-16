import { Link } from '@tanstack/react-router'
import { cn } from '@/lib/utils'
import { ThemePackCard } from './ThemePackCard'
import type { ThemePackEntry } from '../types/ThemePackTypes'

interface ThemePackCardLinkProps {
  packId: string
  packEntry: ThemePackEntry
  className?: string
}

/**
 * Navigation wrapper for ThemePackCard that links to the theme pack detail page.
 */
export function ThemePackCardLink({ packId, packEntry, className }: ThemePackCardLinkProps) {
  return (
    <Link to="/theme-pack/$id" params={{ id: packId }} className={cn('block', className)}>
      <ThemePackCard packId={packId} packEntry={packEntry} enableHoverHighlight />
    </Link>
  )
}
