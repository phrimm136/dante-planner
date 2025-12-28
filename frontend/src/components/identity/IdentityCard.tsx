import type { Identity } from '@/types/IdentityTypes'
import {
  getIdentityInfoImagePath,
  getIdentityImageFallbackPath,
  getUptieFramePath,
  getSinnerBGPath,
  getSinnerIconPath,
  getSelectedIndicatorPath,
} from '@/lib/assetPaths'
import { cn, getSinnerFromId } from '@/lib/utils'

interface IdentityCardProps {
  identity: Identity
  isSelected?: boolean
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
  isSelected = false,
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
          src={getIdentityInfoImagePath(id)}
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
        {/* Selected Indicator - centered on image */}
        {isSelected && (
          <img
            src={getSelectedIndicatorPath()}
            alt="Selected"
            className="absolute w-16 h-16 object-contain pointer-events-none"
          />
        )}
      </div>

      {/* Layer 2: Uptie Frame (transparent border overlay) */}
      <img
        src={getUptieFramePath(rank)}
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
    </div>
  )
}
