import { Suspense, type ReactNode } from 'react'
import type { IdentityListItem } from '@/types/IdentityTypes'
import { Skeleton } from '@/components/ui/skeleton'
import { IdentityName } from './IdentityName'
import {
  getIdentityInfoImagePath,
  getIdentityImageFallbackPath,
  getUptieFramePath,
  getSinnerBGPath,
  getSinnerIconPath,
} from '@/lib/assetPaths'
import { MAX_LEVEL } from '@/lib/constants'
import { cn, getSinnerFromId } from '@/lib/utils'

interface IdentityCardProps {
  identity: IdentityListItem
  /** Override uptie level for image display (uses gacksung at 3+) */
  uptie?: number
  /** Custom overlay content (e.g., selected indicator, deployment badge) */
  overlay?: ReactNode
  /** Additional CSS classes */
  className?: string
}

/**
 * Pure view-only component for rendering an identity card.
 * Does NOT include any interaction logic (Link, onClick, etc.)
 * Parent component is responsible for wrapping with Link, button, or other interactive elements.
 *
 * @example
 * // As a link (use IdentityCardLink)
 * <IdentityCardLink identity={identity} />
 *
 * // With custom wrapper
 * <button onClick={handleSelect}>
 *   <IdentityCard identity={identity} isSelected={true} />
 * </button>
 *
 * // Inside a popover trigger
 * <PopoverTrigger asChild>
 *   <div className="cursor-pointer">
 *     <IdentityCard identity={identity} />
 *   </div>
 * </PopoverTrigger>
 */
export function IdentityCard({
  identity,
  uptie = 4,
  overlay,
  className,
}: IdentityCardProps) {
  const { id, rank } = identity
  const sinner = getSinnerFromId(id)

  return (
    <div
      className={cn(
        'relative w-40 h-56 shrink-0',
        className
      )}
    >
      {/* Clipping container for identity image to fit within frame */}
      <div className="absolute inset-0 flex items-center justify-center overflow-hidden">
        {/* Layer 1: Identity Image (cropped to fit frame) */}
        <img
          src={getIdentityInfoImagePath(id, uptie)}
          onError={(e) => {
            const target = e.currentTarget
            if (!target.dataset.fallback) {
              target.dataset.fallback = 'true'
              target.src = getIdentityImageFallbackPath(id)
            }
          }}
          alt={identity.name}
          loading="lazy"
          className="w-[88%] h-[88%] object-cover"
          style={{ clipPath: 'polygon(4% 0%, 96% 0%, 100% 4%, 100% 96%, 96% 100%, 4% 100%, 0% 96%, 0% 4%)' }}
        />
      </div>

      {/* Custom Overlay - above clipping container */}
      {overlay}

      {/* Layer 2: Uptie Frame (transparent border overlay) */}
      <img
        src={getUptieFramePath(rank, uptie)}
        alt={`${rank} star frame`}
        loading="lazy"
        className="absolute inset-0 w-full h-full object-contain pointer-events-none"
      />

      {/* Layer 3: Sinner BG (upper-right corner, not cropped) */}
      <img
        src={getSinnerBGPath(rank)}
        alt="Sinner background"
        loading="lazy"
        className="absolute -top-2 -right-2 w-14 h-14 object-contain pointer-events-none"
      />

      {/* Layer 4: Sinner Icon (upper-right corner, topmost) */}
      <img
        src={getSinnerIconPath(sinner)}
        alt={sinner}
        loading="lazy"
        className="absolute -top-1 -right-1 w-12 h-12 object-contain pointer-events-none"
      />

      {/* Layer 5: Info Panel (bottom) */}
      <div className="absolute bottom-0 left-0 right-0 flex flex-col items-center pb-1.5 pointer-events-none text-white drop-shadow-[0_1px_2px_rgba(0,0,0,0.8)]">
        {/* Level */}
        <span className="text-[10px] font-semibold">
          Lv. {MAX_LEVEL}
        </span>
        {/* Name - suspends independently for granular loading */}
        <span className="text-[10px] font-medium text-center leading-tight line-clamp-3">
          <Suspense fallback={
            <span className="flex flex-col gap-0.5">
              <Skeleton className="w-16 h-2.5 bg-white/30" />
              <Skeleton className="w-12 h-2.5 bg-white/30" />
            </span>
          }>
            <IdentityName id={id} />
          </Suspense>
        </span>
      </div>
    </div>
  )
}
