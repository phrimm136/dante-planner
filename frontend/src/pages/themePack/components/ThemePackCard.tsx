import { Suspense, type ReactNode } from 'react'
import {
  getThemePackImagePath,
  getThemePackHoverPath,
  getThemePackFocusedPath,
  getThemePackHoverExtremePath,
} from '@/shared/assets'
import { aspectOf, layerStyle, pctStyle } from '@/shared/cardLayout'
import { THEME_PACK_GEOMETRY } from '../lib/cardLayout'
import { cn } from '@/lib/utils'
import { Skeleton } from '@/components/ui/skeleton'
import { isExtremePack } from '../types/ThemePackTypes'
import type { ThemePackEntry } from '../types/ThemePackTypes'
import { THEME_PACK_ART, THEME_PACK_HOVER_FADE_MS, THEME_PACK_LAYOUT } from '../lib/cardLayout'
import { ThemePackName } from './ThemePackName'

interface ThemePackCardProps {
  packId: string
  packEntry: ThemePackEntry
  /** Fade the hover sprite in while the pointer is over the card. */
  enableHoverHighlight?: boolean
  /** Hold the focused sprite on, for click-to-pin selection. */
  isSelected?: boolean
  /** Extra content drawn above every card layer. */
  overlay?: ReactNode
  className?: string
}

/**
 * A theme pack card at the game's geometry: the root is the game's `[Rect]Cards`, filled
 * by the composed pack art, with the name and the highlight sprites over it.
 */
export function ThemePackCard({
  packId,
  packEntry,
  enableHoverHighlight = false,
  isSelected = false,
  overlay,
  className,
}: ThemePackCardProps) {
  const isExtreme = isExtremePack(packEntry)
  const layout = isExtreme ? THEME_PACK_LAYOUT.extreme : THEME_PACK_LAYOUT.normal
  const hoverSrc = isExtreme ? getThemePackHoverExtremePath() : getThemePackHoverPath()
  const focusedSrc = isExtreme ? getThemePackHoverExtremePath() : getThemePackFocusedPath()

  return (
    <div
      className={cn('group relative w-full', className)}
      style={{
        containerType: 'inline-size',
        aspectRatio: aspectOf(THEME_PACK_GEOMETRY.size),
      }}
    >
      <img
        src={getThemePackImagePath(packId)}
        alt=""
        loading="lazy"
        style={layerStyle(THEME_PACK_ART)}
      />

      {enableHoverHighlight && (
        <img
          src={hoverSrc}
          alt=""
          className={cn(
            'pointer-events-none opacity-0 transition-opacity',
            'group-hover:opacity-100 group-active:opacity-100',
          )}
          style={{
            ...layerStyle(layout.overlay),
            transitionDuration: `${String(THEME_PACK_HOVER_FADE_MS)}ms`,
          }}
        />
      )}

      {isSelected && (
        <img
          src={focusedSrc}
          alt=""
          className="pointer-events-none"
          style={layerStyle(layout.overlay)}
        />
      )}

      <div
        className="flex items-center justify-center pointer-events-none"
        style={pctStyle(layout.name)}
      >
        <Suspense fallback={<Skeleton className="h-full w-full bg-foreground" />}>
          <ThemePackName packId={packId} packEntry={packEntry} rect={layout.name} />
        </Suspense>
      </div>

      {overlay}
    </div>
  )
}
