import { useTranslation } from 'react-i18next'

import {
  getEGORankIconPath,
  getEGODetailImagePath,
  getSinnerIconPath,
  getSinnerIconRingPath,
} from '@/shared/assets'
import { CharacterImageSection } from '@/components/layout/CharacterImageSection'
import { Skeleton } from '@/components/ui/skeleton'
import { type Sinner } from '@/shared/gameData'
import { getSinnerFromId } from '@/shared/gameData'
import { getDisplayFontForLanguage } from '@/lib/utils'
import type { EgoType } from '@/shared/gameData'
import type { EgoSkillType } from '../types/EGOTypes'
import { DETAIL_IMAGE_ASPECT_RATIO, SECTION_STYLES, SINNER_COLORS } from '@/lib/constants'
import type { EGOId } from '@/shared/gameData'

interface EGOHeaderProps {
  egoId: EGOId
  name: string
  rank: EgoType
  skillType: EgoSkillType
}

export function EGOHeader({ egoId, name, rank, skillType }: EGOHeaderProps) {
  const { i18n } = useTranslation()
  const sinner = getSinnerFromId(egoId) as Sinner
  const sinnerColor = SINNER_COLORS[sinner] || '#333333'
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  const frameRank = 3

  // Not every EGO with erosion skills has a corrosion CG.
  const src = getEGODetailImagePath(egoId, skillType)
  const fallbackSrc = skillType === 'erosion' ? getEGODetailImagePath(egoId, 'awaken') : undefined

  return (
    <div className="space-y-4">
      <div>
        <div className="flex justify-end">
          <img src={getEGORankIconPath(rank)} alt={`${rank} rank`} className="h-6 object-contain" />
        </div>
        <div className="flex items-center gap-3">
          <div className="relative w-12 h-12 flex-shrink-0">
            <img
              src={getSinnerIconRingPath(frameRank)}
              alt=""
              className="absolute inset-0 w-full h-full object-contain"
            />
            <img
              src={getSinnerIconPath(sinner)}
              alt={sinner}
              className="absolute inset-0 w-full h-full object-contain p-1"
            />
          </div>
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

      <CharacterImageSection
        src={src}
        fallbackSrc={fallbackSrc}
        alt={name}
        aspectRatio={DETAIL_IMAGE_ASPECT_RATIO.EGO}
      />
    </div>
  )
}
