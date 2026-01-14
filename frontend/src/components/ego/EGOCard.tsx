import { Suspense, type ReactNode } from 'react'
import type { EGOListItem } from '@/types/EGOTypes'
import {
  getEGOImagePath,
  getEGOFramePath,
  getEGORankIconPath,
  getEGOSmallRankIconPath,
  getEGOTierIconPath,
  getEGOInfoPanelPath,
  getSinnerIconPath,
  getSinnerBGPath,
} from '@/lib/assetPaths'
import { EGO_DEFAULT_THREADSPIN_TIER } from '@/lib/constants'
import { cn, getSinnerFromId } from '@/lib/utils'
import { EGOName } from './EGOName'
import { Skeleton } from '@/components/ui/skeleton'

interface EGOCardProps {
  /** The EGO data to display */
  ego: EGOListItem
  /** Custom overlay content (e.g., selected indicator) */
  overlay?: ReactNode
  /** Additional CSS classes for styling flexibility */
  className?: string
}

/**
 * Pure view-only component for rendering an EGO card.
 * Does NOT include any interaction logic (Link, onClick, etc.)
 * Parent component is responsible for wrapping with Link, button, or other interactive elements.
 *
 * @example
 * // As a link (use EGOCardLink)
 * <EGOCardLink ego={ego} />
 *
 * // With custom wrapper
 * <button onClick={handleSelect}>
 *   <EGOCard ego={ego} isSelected={true} />
 * </button>
 *
 * // Inside a popover trigger
 * <PopoverTrigger asChild>
 *   <div className="cursor-pointer">
 *     <EGOCard ego={ego} />
 *   </div>
 * </PopoverTrigger>
 */
export function EGOCard({
  ego,
  overlay,
  className,
}: EGOCardProps) {
  const { id, egoType: rank, attributeTypes } = ego
  const sinner = getSinnerFromId(id)

  return (
    <div
      className={cn(
        'relative w-40 h-48 shrink-0',
        className
      )}
    >
      {/* Layer 1: Circular EGO Image */}
      <div className="absolute inset-0 flex items-center justify-center">
        <img
          src={getEGOImagePath(id)}
          alt="EGO"
          loading="lazy"
          className="w-36 h-36 object-cover rounded-full"
        />
      </div>

      {/* Custom Overlay - above image */}
      {overlay}


      {/* Layer 2: Static EGO Frame */}
      <img
        src={getEGOFramePath()}
        alt="EGO Frame"
        loading="lazy"
        className="absolute inset-0 w-38 h-38 object-cover top-5 left-0.5 pointer-events-none"
      />

      {/* Layer 3: Sinner Background (upper-center) */}
      <img
        src={getSinnerBGPath(1)}
        alt="Sinner background"
        loading="lazy"
        className="absolute top-1 left-1/2 -translate-x-1/2 w-11 h-11 object-contain pointer-events-none"
      />

      {/* Layer 4: Sinner Icon (upper-center) */}
      <img
        src={getSinnerIconPath(sinner)}
        alt={sinner}
        loading="lazy"
        className="absolute top-2 left-1/2 -translate-x-1/2 w-9 h-9 object-contain pointer-events-none"
      />

      {/* Layer 5: Info Panel (bottom) with sin-colored background */}
      <div className="absolute bottom-1 left-0 right-0 h-12 w-32 translate-x-4 pointer-events-none">
        {/* Sin-colored panel background */}
        <img
          src={getEGOInfoPanelPath(attributeTypes[0])}
          alt="Info panel"
          loading="lazy"
          className="absolute inset-0 items-center object-cover"
        />

        {/* Panel content - three sections */}
        <div className="absolute left-0 translate-y-4.5 -translate-x-[0px] inset-0 flex items-center w-32 h-8">
          {/* Left: Small Rank Icon */}
          <div className="items-center w-8 h-8 pl-1">
            <img
              src={getEGOSmallRankIconPath(rank)}
              alt={rank}
              loading="lazy"
              className="w-4 h-4 translate-x-1 translate-y-0.5 object-contain"
              style={{ transform: 'skewY(20deg)' }}
            />
          </div>

          {/* Center: EGO Name */}
          <div className="flex text-center justify-center items-center w-[77px] h-8 text-shadow-black text-shadow-xs">
            <Suspense fallback={<Skeleton className="w-12 h-3 inline-block bg-foreground" />}>
              <EGOName id={id} />
            </Suspense>
          </div>

          {/* Right: Tier Icon (stretched/tilted) */}
          <div className="items-center w-8 h-8 pl-1">
            <img
              src={getEGOTierIconPath(EGO_DEFAULT_THREADSPIN_TIER)}
              alt={`Tier ${EGO_DEFAULT_THREADSPIN_TIER}`}
              loading="lazy"
              className="w-5 h-5 translate-x-0.5 translate-y-0.5 object-contain"
              style={{ transform: 'skewY(-20deg)' }}
            />
          </div>
        </div>
      </div>

      {/* Layer 6: Large Rank Indicator (above info panel) */}
      <div className="absolute bottom-4.25 left-1/2 -translate-x-1/2 w-12 h-12 pointer-events-none">
        <img
          src={getEGORankIconPath(rank)}
          alt={`Rank ${rank}`}
          loading="lazy"
          className="w-12 h-12 object-contain"
        />
      </div>
    </div>
  )
}
