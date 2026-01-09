import {
  getEGORankIconPath,
  getEGODetailImagePath,
  getSinnerIconPath,
  getSinnerBGPath,
} from '@/lib/assetPaths'
import { Skeleton } from '@/components/ui/skeleton'
import { SINNER_COLORS, type Sinner } from '@/lib/constants'
import { getSinnerFromId } from '@/lib/utils'
import type { EGOType } from '@/types/EGOTypes'

interface EGOHeaderProps {
  egoId: string
  name: string
  rank: EGOType
}

/**
 * EGOHeader - Two-row header matching Identity UI style
 *
 * Row 1: Rank icon (right-aligned)
 * Row 2: Sinner icon with rank-aware frame (left) + EGO name (right)
 */
export function EGOHeader({ egoId, name, rank }: EGOHeaderProps) {
  // Derive sinner from EGO ID and get color
  const sinner = getSinnerFromId(egoId) as Sinner
  const sinnerColor = SINNER_COLORS[sinner] || '#333333'

  // All EGOs use rank 3 frame (highest tier)
  const frameRank = 3

  const handleExpandImage = () => {
    const imagePath = getEGODetailImagePath(egoId)
    window.open(imagePath, '_blank')
  }

  return (
    <div className="space-y-4">
      {/* Title Area: Two rows */}
      <div>
        {/* Row 1: Rank icon on the right */}
        <div className="flex justify-end">
          <img
            src={getEGORankIconPath(rank)}
            alt={`${rank} rank`}
            className="h-6 object-contain"
          />
        </div>
        {/* Row 2: Sinner icon + EGO name */}
        <div className="flex items-center gap-3">
          {/* Sinner Icon with layered frame (rank aware) */}
          <div className="relative w-12 h-12 flex-shrink-0">
            {/* Background layer */}
            <img
              src={getSinnerBGPath(frameRank)}
              alt=""
              className="absolute inset-0 w-full h-full object-contain"
            />
            {/* Sinner icon layer */}
            <img
              src={getSinnerIconPath(sinner)}
              alt={sinner}
              className="absolute inset-0 w-full h-full object-contain p-1"
            />
          </div>
          {/* EGO name with sinner color */}
          {name ? (
            <h1
              className="text-2xl font-bold"
              style={{ color: sinnerColor }}
            >
              {name}
            </h1>
          ) : (
            <Skeleton className="h-8 w-48" style={{ backgroundColor: sinnerColor }} />
          )}
        </div>
      </div>

      {/* Character Image with expand button */}
      <div className="relative bg-muted rounded-lg overflow-hidden">
        <img
          src={getEGODetailImagePath(egoId)}
          alt={name}
          className="w-full h-auto object-contain"
        />

        {/* Expand button */}
        <div className="absolute top-4 left-4">
          <button
            onClick={handleExpandImage}
            className="relative w-12 h-12"
            style={{
              backgroundImage: 'url(/images/UI/common/button.webp)',
              backgroundSize: 'cover',
              backgroundPosition: 'center',
            }}
          >
            <img
              src="/images/UI/common/buttonExpandImage.webp"
              alt="Expand image"
              className="w-full h-full object-contain p-2"
            />
          </button>
        </div>
      </div>
    </div>
  )
}
