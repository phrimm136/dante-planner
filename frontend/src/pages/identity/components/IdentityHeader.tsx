import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  getRarityIconPath,
  getIdentityDetailImagePath,
  getSinnerIconPath,
  getSinnerIconRingPath,
} from '@/shared/assets'
import { CharacterImageSection } from '@/components/layout/CharacterImageSection'
import { Skeleton } from '@/components/ui/skeleton'
import { type Sinner } from '@/shared/gameData'
import { getSinnerFromId } from '@/shared/gameData'
import { getDisplayFontForLanguage } from '@/lib/utils'
import { DETAIL_IMAGE_ASPECT_RATIO, SECTION_STYLES, SINNER_COLORS } from '@/lib/constants'
import type { IdentityId } from '@/shared/gameData'

type ImageVariant = 'normal' | 'gacksung'

interface IdentityHeaderProps {
  identityId: IdentityId
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
  const [imageVariant, setImageVariant] = useState<ImageVariant>(
    canShowGacksung ? 'gacksung' : 'normal',
  )
  const [appliedGacksung, setAppliedGacksung] = useState(canShowGacksung)

  // Availability changing discards a manual swap and re-picks the default.
  if (canShowGacksung !== appliedGacksung) {
    setAppliedGacksung(canShowGacksung)
    setImageVariant(canShowGacksung ? 'gacksung' : 'normal')
  }

  // Derive sinner from identity ID and get color
  const sinner = getSinnerFromId(identityId) as Sinner
  const sinnerColor = SINNER_COLORS[sinner] || '#333333'
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  const handleSwapImage = () => {
    setImageVariant((prev: ImageVariant) => (prev === 'gacksung' ? 'normal' : 'gacksung'))
  }

  const currentImagePath = getIdentityDetailImagePath(identityId, imageVariant)

  return (
    <div className="space-y-4">
      {/* Title Area: Two rows */}
      <div>
        {/* Row 1: Rank icon on the right */}
        <div className="flex justify-end">
          <img src={getRarityIconPath(rank)} alt={`${rank} rank`} className="h-6 object-contain" />
        </div>
        {/* Row 2: Sinner icon + Identity name */}
        <div className="flex items-center gap-3">
          {/* Sinner Icon with layered frame (rank aware) */}
          <div className="relative w-12 h-12 flex-shrink-0">
            {/* Background layer */}
            <img
              src={getSinnerIconRingPath(rank)}
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
              className={SECTION_STYLES.TEXT.pageTitle}
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
      <CharacterImageSection
        src={currentImagePath}
        fallbackSrc={getIdentityDetailImagePath(identityId, 'normal')}
        onFallback={() => {
          setImageVariant('normal')
        }}
        alt={name}
        aspectRatio={DETAIL_IMAGE_ASPECT_RATIO.IDENTITY}
        swap={{ onSwap: handleSwapImage, disabled: !canShowGacksung }}
      />
    </div>
  )
}
