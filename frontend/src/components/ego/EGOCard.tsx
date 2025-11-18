import { Link } from '@tanstack/react-router'
import type { EGO } from '@/types/EGOTypes'
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

interface EGOCardProps {
  ego: EGO
}

const DEFAULT_TIER = 4

export function EGOCard({ ego }: EGOCardProps) {
  const { id, name, rank, sin, sinner } = ego

  return (
    <Link
      to="/ego/$id"
      params={{ id }}
      className="block relative w-40 h-48 shrink-0"
    >
      {/* Layer 1: Circular EGO Image */}
      <div className="absolute inset-0 flex items-center justify-center">
        <img
          src={getEGOImagePath(id)}
          alt={name}
          className="w-36 h-36 object-cover rounded-full"
        />
      </div>

      {/* Layer 2: Static EGO Frame */}
      <img
        src={getEGOFramePath()}
        alt="EGO Frame"
        className="absolute inset-0 w-full h-full object-contain pointer-events-none"
      />

      {/* Layer 3: Sinner Background (upper-center) */}
      <img
        src={getSinnerBGPath(1)}
        alt="Sinner background"
        className="absolute top-1 left-1/2 -translate-x-1/2 w-8 h-8 object-contain pointer-events-none"
      />

      {/* Layer 4: Sinner Icon (upper-center) */}
      <img
        src={getSinnerIconPath(sinner)}
        alt={sinner}
        className="absolute top-2 left-1/2 -translate-x-1/2 w-6 h-6 object-contain pointer-events-none"
      />

      {/* Layer 5: Info Panel (bottom) with sin-colored background */}
      <div className="absolute bottom-0 left-0 right-0 h-12 w-32 translate-x-4 pointer-events-none">
        {/* Sin-colored panel background */}
        <img
          src={getEGOInfoPanelPath(sin)}
          alt="Info panel"
          className="absolute inset-0 items-center object-cover"
        />

        {/* Panel content - three sections */}
        <div className="absolute translate-y-4.5 inset-0 flex items-center w-32 h-8">
          {/* Left: Small Rank Icon */}
          <div className="items-center w-8 h-8 pl-1">
            <img
              src={getEGOSmallRankIconPath(rank)}
              alt={rank}
              className="w-4 h-4 translate-x-1 translate-y-0.5 object-contain"
              style={{ transform: 'skewY(20deg)' }}
            />
          </div>

          {/* Center: EGO Name - TODO: size will be adjusted later during master up */}
          <div className="text-center w-16 h-8">
            <span className="text-[9px] font-semibold text-white line-clamp-3 block">{name}</span>
          </div>

          {/* Right: Tier Icon (stretched/tilted) */}
          <div className="items-center w-8 h-8 pl-1">
            <img
              src={getEGOTierIconPath(DEFAULT_TIER)}
              alt={`Tier ${DEFAULT_TIER}`}
              className="w-5 h-5 translate-x-0.5 translate-y-0.5 object-contain"
              style={{ transform: 'skewY(-20deg)' }}
            />
          </div>
        </div>
      </div>

      {/* Layer 6: Large Rank Indicator (above info panel) */}
      <div className="absolute bottom-3.75 left-1/2 -translate-x-1/2 w-12 h-12 pointer-events-none">
        <img
          src={getEGORankIconPath(rank)}
          alt={`Rank ${rank}`}
          className="w-12 h-12 object-contain"
        />
      </div>
    </Link>
  )
}
