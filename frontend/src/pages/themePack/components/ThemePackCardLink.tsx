import { Link } from '@tanstack/react-router'
import { ThemePackCard } from './ThemePackCard'
import type { ThemePackEntry } from '../types/ThemePackTypes'
import { cn } from '@/lib/utils'

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
    <Link to="/theme-pack/$id" params={{ id: packId }} className={cn(className)}>
      <ThemePackCard packId={packId} packEntry={packEntry} enableHoverHighlight />
    </Link>
  )
}
