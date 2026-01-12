import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  getRarityIconPath,
  getIdentityDetailImagePath,
  getSinnerIconPath,
  getSinnerBGPath,
} from '@/lib/assetPaths'
import { Skeleton } from '@/components/ui/skeleton'
import { SINNER_COLORS, type Sinner } from '@/lib/constants'
import { getSinnerFromId, getDisplayFontForLanguage } from '@/lib/utils'

type ImageVariant = 'normal' | 'gacksung'

interface IdentityHeaderProps {
  identityId: string
  name: string
  rank: number
  uptie: number
}

/**
 * IdentityHeader - Two-row header matching game UI style
 *
 * Row 1: Rank icon (right-aligned)
 * Row 2: Sinner icon with rank+uptie frame (left) + Identity name (right)
 */
export function IdentityHeader({ identityId, name, rank, uptie }: IdentityHeaderProps) {
  const { i18n } = useTranslation()
  // Gacksung image only available for rank > 1 AND uptie >= 3
  const canShowGacksung = rank > 1 && uptie >= 3
  const [imageVariant, setImageVariant] = useState<ImageVariant>(canShowGacksung ? 'gacksung' : 'normal')

  // Derive sinner from identity ID and get color
  const sinner = getSinnerFromId(identityId) as Sinner
  const sinnerColor = SINNER_COLORS[sinner] || '#333333'
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  // Sync image variant with gacksung availability
  useEffect(() => {
    setImageVariant(canShowGacksung ? 'gacksung' : 'normal')
  }, [canShowGacksung])

  const handleSwapImage = () => {
    setImageVariant((prev: ImageVariant) => (prev === 'gacksung' ? 'normal' : 'gacksung'))
  }

  const handleExpandImage = () => {
    const imagePath = getIdentityDetailImagePath(identityId, imageVariant)
    window.open(imagePath, '_blank')
  }

  const currentImagePath = getIdentityDetailImagePath(identityId, imageVariant)

  return (
    <div className="space-y-4">
      {/* Title Area: Two rows */}
      <div>
        {/* Row 1: Rank icon on the right */}
        <div className="flex justify-end">
          <img
            src={getRarityIconPath(rank)}
            alt={`${rank} rank`}
            className="h-6 object-contain"
          />
        </div>
        {/* Row 2: Sinner icon + Identity name */}
        <div className="flex items-center gap-3">
          {/* Sinner Icon with layered frame (rank aware) */}
          <div className="relative w-12 h-12 flex-shrink-0">
            {/* Background layer */}
            <img
              src={getSinnerBGPath(rank)}
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
          {/* Identity name with sinner color */}
          {name ? (
            <h1
              className="text-2xl font-bold"
              style={{ color: sinnerColor, ...displayStyle }}
            >
              {name}
            </h1>
          ) : (
            <Skeleton className="h-8 w-48" style={{ backgroundColor: sinnerColor }} />
          )}
        </div>
      </div>

      {/* Character Image with overlay buttons */}
      <div className="relative bg-muted rounded-lg overflow-hidden">
        <img
          src={currentImagePath}
          alt={name}
          className="w-full h-auto object-contain"
          onError={(e) => {
            // Fallback to normal variant if gacksung fails
            if (imageVariant === 'gacksung') {
              const img = e.target as HTMLImageElement
              img.src = getIdentityDetailImagePath(identityId, 'normal')
              setImageVariant('normal')
            }
          }}
        />

        {/* Stacked buttons */}
        <div className="absolute top-4 left-4 flex flex-col gap-2">
          {/* Swap button - disabled when gacksung image not available */}
          <button
            onClick={handleSwapImage}
            disabled={!canShowGacksung}
            className="relative w-12 h-12 disabled:opacity-50 disabled:cursor-not-allowed"
            style={{
              backgroundImage: 'url(/images/UI/common/button.webp)',
              backgroundSize: 'cover',
              backgroundPosition: 'center',
            }}
          >
            <img
              src="/images/UI/common/buttonSwapImage.webp"
              alt="Swap image"
              className="w-full h-full object-contain p-2"
            />
          </button>

          {/* Expand button */}
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
