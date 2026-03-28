import { memo } from 'react'
import { Link } from '@tanstack/react-router'
import { ThemePackCard } from '@/components/floorTheme/ThemePackCard'
import type { ThemePackEntry } from '@/types/ThemePackTypes'
import { cn } from '@/lib/utils'

interface ThemePackCardLinkProps {
  packId: string
  packEntry: ThemePackEntry
  packName: string
  specialName?: string
  className?: string
}

/**
 * Navigation wrapper for ThemePackCard that links to the theme pack detail page.
 * Memoized by packId to prevent re-renders during list filtering.
 */
export const ThemePackCardLink = memo(function ThemePackCardLink({
  packId,
  packEntry,
  packName,
  specialName,
  className,
}: ThemePackCardLinkProps) {
  return (
    <Link
      to="/theme-pack/$id"
      params={{ id: packId }}
      className={cn(className)}
    >
      <ThemePackCard
        packId={packId}
        packEntry={packEntry}
        packName={packName}
        specialName={specialName}
        enableHoverHighlight
      />
    </Link>
  )
}, (prev, next) => prev.packId === next.packId && prev.packName === next.packName)
