import { Suspense, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import type { IdentityListItem } from '@/types/IdentityTypes'
import { Skeleton } from '@/components/ui/skeleton'
import { IdentityName } from './IdentityName'
import {
  getIdentityInfoImagePath,
  getIdentityImageFallbackPath,
  getUptieFramePath,
  getIdentityFrameHighlightPath,
  getSinnerBGPath,
  getSinnerIconPath,
} from '@/lib/assetPaths'
import { MAX_LEVEL } from '@/lib/constants'
import { cn, getSinnerFromId, getDisplayFontForNumeric } from '@/lib/utils'
import { getRarityIconPath } from '@/lib/assetPaths'

interface IdentityCardProps {
  identity: IdentityListItem
  /** Override uptie level for image display (uses gacksung at 3+) */
  uptie?: number
  /** Dim the entire card (selected/equipped state) */
  isSelected?: boolean
  /** Show highlight frame (hover state) */
  isHighlighted?: boolean
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
  isSelected = false,
  isHighlighted = false,
  overlay,
  className,
}: IdentityCardProps) {
  const { t } = useTranslation()
  const { id, rank } = identity
  const sinner = getSinnerFromId(id)

  return (
    <div
      className={cn(
        'relative w-40 h-56 shrink-0',
        className
      )}
    >
      {/* Card content wrapper - dimmed when selected */}
      <div className={cn('absolute inset-0', isSelected && 'brightness-50')}>
        {/* Clipping container for identity image to fit within frame */}
        <div className="absolute inset-0 flex items-start justify-center overflow-hidden">
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
            className="w-[88%] h-[96%] object-cover mt-1.5"
            style={{ clipPath: 'polygon(4% 0%, 96% 0%, 100% 4%, 100% 96%, 96% 100%, 4% 100%, 0% 96%, 0% 4%)' }}
          />
        </div>

        {/* Layer 2: Uptie Frame (transparent border overlay) */}
        <img
          src={getUptieFramePath(rank, uptie)}
          alt={`${rank} star frame`}
          loading="lazy"
          className="absolute inset-0 w-full h-full object-contain pointer-events-none"
        />

        {/* Layer 2.5: Identity Highlight Frame (glowing border) */}
        {isHighlighted && (
          <img
            src={getIdentityFrameHighlightPath()}
            alt=""
            loading="lazy"
            className="absolute inset-0 w-full h-full object-contain pointer-events-none"
          />
        )}

        {/* Layer 3: Sinner BG (upper-right corner, not cropped) */}
        <img
          src={getSinnerBGPath(rank)}
          alt={t('a11y.sinnerBackground')}
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

        {/* Layer 5: Info Panel (bottom-right, game-style) */}
        <div className="absolute bottom-3 right-5 flex flex-col items-end pointer-events-none drop-shadow-[0_1px_2px_rgba(0,0,0,0.8)]">
          {/* Level */}
          <div className="text-[24px] mb-[2px]" style={{ fontFamily: getDisplayFontForNumeric() }}>
            {`Lv. ${MAX_LEVEL}`}
          </div>
          {/* Name - suspends independently for granular loading */}
          <span>
            <Suspense fallback={
              <span className="flex flex-col items-end gap-0.5">
                <Skeleton className="w-14 h-2.5 bg-white/30" />
                <Skeleton className="w-10 h-2.5 bg-white/30" />
              </span>
            }>
              <IdentityName id={id} />
            </Suspense>
          </span>
        </div>

        {/* Layer 6 - Identity Rank Indicator (top-left) */}
        <div className="absolute top-3.5 left-3.5 pointer-events-none">
          <img
            src={getRarityIconPath(rank)}
            alt={String(rank)}
            className="h-6"
          />
        </div>
      </div>

      {/* Layer 7: Custom Overlay (topmost layer, not dimmed) */}
      {overlay}
    </div>
  )
}
