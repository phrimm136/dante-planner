import { getEGORankIconPath, getEGODetailImagePath } from '@/shared/assets'
import { CharacterImageSection } from '@/components/layout/CharacterImageSection'
import { type Sinner } from '@/shared/gameData'
import { getSinnerFromId } from '@/shared/gameData'
import type { EgoType } from '@/shared/gameData'
import type { EgoSkillType } from '../types/EGOTypes'
import { DETAIL_IMAGE_ASPECT_RATIO } from '@/lib/constants'
import type { EGOId } from '@/shared/gameData'
import { SinnerTitleRow } from '@/shared/sinner'

interface EGOHeaderProps {
  egoId: EGOId
  name: string
  rank: EgoType
  skillType: EgoSkillType
}

export function EGOHeader({ egoId, name, rank, skillType }: EGOHeaderProps) {
  const sinner = getSinnerFromId(egoId) as Sinner

  const frameRank = 3

  // Not every EGO with erosion skills has a corrosion CG.
  const src = getEGODetailImagePath(egoId, skillType)
  const fallbackSrc = skillType === 'erosion' ? getEGODetailImagePath(egoId, 'awaken') : undefined

  return (
    <div className="space-y-4">
      <SinnerTitleRow
        sinner={sinner}
        name={name}
        rank={rank}
        rankIconSrc={getEGORankIconPath(rank)}
        ringRank={frameRank}
      />

      <CharacterImageSection
        src={src}
        fallbackSrc={fallbackSrc}
        alt={name}
        aspectRatio={DETAIL_IMAGE_ASPECT_RATIO.EGO}
      />
    </div>
  )
}
