import { memo } from 'react'
import { Link } from '@tanstack/react-router'
import type { Identity } from '@/types/IdentityTypes'
import {
  getIdentityImagePath,
  getIdentityImageFallbackPath,
  getUptieFramePath,
  getSinnerBGPath,
  getSinnerIconPath,
} from '@/lib/assetPaths'
import { cn, getSinnerFromId } from '@/lib/utils'

interface IdentityCardProps {
  identity: Identity
  isSelected?: boolean
  onSelect?: (identity: Identity) => void
}

export const IdentityCard = memo(function IdentityCard({
  identity,
  isSelected = false,
  onSelect,
}: IdentityCardProps) {
  const { id, rank } = identity
  const sinner = getSinnerFromId(id)

  // If onSelect is provided, prevent navigation and call onSelect instead
  const handleClick = onSelect
    ? (e: React.MouseEvent) => {
        e.preventDefault()
        onSelect(identity)
      }
    : undefined

  return (
    <Link
      to="/identity/$id"
      params={{ id }}
      className={cn(
        'block relative w-40 h-56 shrink-0 transition-all',
        onSelect && 'hover:scale-105'
      )}
      onClick={handleClick}
    >
      {/* Clipping container for identity image to fit within frame */}
      <div className="absolute inset-0 flex items-center justify-center overflow-hidden">
        {/* Layer 1: Identity Image (cropped to fit frame) */}
        <img
          src={getIdentityImagePath(id)}
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
            src="/images/UI/formation/selected.webp"
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
    </Link>
  )
})
