import { useTranslation } from 'react-i18next'

import {
  getEGORankIconPath,
  getEGODetailImagePath,
  getSinnerIconPath,
  getSinnerBGPath,
} from '@/shared/assets'
import { CharacterImageSection } from '@/components/layout/CharacterImageSection'
import { Skeleton } from '@/components/ui/skeleton'
import { type Sinner } from '@/shared/gameData'
import { getSinnerFromId } from '@/shared/gameData'
import { getDisplayFontForLanguage } from '@/lib/utils'
import type { EgoType } from '@/shared/gameData'
import type { EgoSkillType } from '../types/EGOTypes'
import { DETAIL_IMAGE_ASPECT_RATIO, SECTION_STYLES, SINNER_COLORS } from '@/lib/constants'

interface EGOHeaderProps {
  egoId: string
  name: string
  rank: EgoType
  /** Selected skill type; erosion shows the corrosion CG */
  skillType: EgoSkillType
}

/**
 * EGOHeader - Two-row header matching Identity UI style
 *
 * Row 1: Rank icon (right-aligned)
 * Row 2: Sinner icon with rank-aware frame (left) + EGO name (right)
 */
export function EGOHeader({ egoId, name, rank, skillType }: EGOHeaderProps) {
  const { i18n } = useTranslation()
  // Derive sinner from EGO ID and get color
  const sinner = getSinnerFromId(egoId) as Sinner
  const sinnerColor = SINNER_COLORS[sinner] || '#333333'
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  // All EGOs use rank 3 frame (highest tier)
  const frameRank = 3

  // Not every EGO with erosion skills has a corrosion CG.
  const src = getEGODetailImagePath(egoId, skillType)
  const fallbackSrc = skillType === 'erosion' ? getEGODetailImagePath(egoId, 'awaken') : undefined

  return (
    <div className="space-y-4">
      {/* Title Area: Two rows */}
      <div>
        {/* Row 1: Rank icon on the right */}
        <div className="flex justify-end">
          <img src={getEGORankIconPath(rank)} alt={`${rank} rank`} className="h-6 object-contain" />
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

      {/* Character Image with expand button */}
      <CharacterImageSection
        src={src}
        fallbackSrc={fallbackSrc}
        alt={name}
        aspectRatio={DETAIL_IMAGE_ASPECT_RATIO.EGO}
      />
    </div>
  )
}
