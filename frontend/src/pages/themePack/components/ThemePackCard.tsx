import { Suspense, type ReactNode } from 'react'
import {
  getThemePackImagePath,
  getThemePackHoverHighlightPath,
  getThemePackSelectHighlightPath,
  getThemePackExtremeHighlightPath,
} from '@/shared/assets'
import { cn } from '@/lib/utils'
import { Skeleton } from '@/components/ui/skeleton'
import { isExtremePack } from '../types/ThemePackTypes'
import type { ThemePackEntry } from '../types/ThemePackTypes'
import { ThemePackName } from './ThemePackName'

interface ThemePackCardProps {
  packId: string
  packEntry: ThemePackEntry
  /** Enable hover highlight overlay (for selection contexts) */
  enableHoverHighlight?: boolean
  /** Show persistent select highlight (for click-to-pin focus) */
  isSelected?: boolean
  /** Custom overlay content (e.g., selected indicator) */
  overlay?: ReactNode
  className?: string
}

/**
 * Pure view component for rendering a theme pack card.
 * Does NOT include any interaction logic.
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

  // Normal frame: 404x716, Extreme frame: 749x1247
  const normalStyle = { left: '3.22%', top: '0.8%', width: '94.06%', height: '97.2%' }
  const extremeStyle = { left: '-2.22%', top: '-1.3%', width: '104.06%', height: '101.37%' }

  return (
    <div className={cn('group relative w-60 aspect-[416/684]', className)}>
      {/* Layer 1: Theme pack image - static to define container size */}
      <img src={getThemePackImagePath(packId)} alt="" loading="lazy" className="w-full h-auto" />

      {/* Layer 2: Select highlight overlay */}
      {isSelected && (
        <img
          src={isExtreme ? getThemePackExtremeHighlightPath() : getThemePackSelectHighlightPath()}
          alt=""
          className="absolute max-w-none object-fill pointer-events-none"
          style={isExtreme ? extremeStyle : normalStyle}
        />
      )}

      {/* Layer 3: Hover highlight overlay */}
      {(enableHoverHighlight || isSelected) && (
        <img
          src={isExtreme ? getThemePackExtremeHighlightPath() : getThemePackHoverHighlightPath()}
          alt=""
          className="absolute max-w-none object-fill pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity"
          style={isExtreme ? extremeStyle : normalStyle}
        />
      )}

      {/* Layer 4: Theme pack name */}
      <div
        className="absolute left-0 right-0 flex justify-center items-center pointer-events-none leading-4"
        style={{
          top: !isExtreme ? '74.586%' : '81.960%',
          height: !isExtreme ? '8.544%' : '10.010%',
        }}
      >
        <Suspense fallback={<Skeleton className="h-5 w-40 bg-foreground" />}>
          <ThemePackName packId={packId} packEntry={packEntry} />
        </Suspense>
      </div>

      {/* Layer 5: Custom overlay */}
      {overlay}
    </div>
  )
}
