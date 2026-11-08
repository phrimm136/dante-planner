import { useState } from 'react'

import { getRarityIconPath, getIdentityDetailImagePath } from '@/shared/assets'
import { CharacterImageSection } from '@/components/layout/CharacterImageSection'
import { type Sinner } from '@/shared/gameData'
import { getSinnerFromId } from '@/shared/gameData'
import { DETAIL_IMAGE_ASPECT_RATIO } from '@/lib/constants'
import type { IdentityId } from '@/shared/gameData'
import { SinnerTitleRow } from '@/shared/sinner'

type ImageVariant = 'normal' | 'gacksung'

interface IdentityHeaderProps {
  identityId: IdentityId
  name: string
  rank: number
  uptie: number
}

export function IdentityHeader({ identityId, name, rank, uptie }: IdentityHeaderProps) {
  // Gacksung image only available for rank > 1 AND uptie >= 3
  const canShowGacksung = rank > 1 && uptie >= 3
  const [imageVariant, setImageVariant] = useState<ImageVariant>(
    canShowGacksung ? 'gacksung' : 'normal',
  )
  const [appliedGacksung, setAppliedGacksung] = useState(canShowGacksung)

  if (canShowGacksung !== appliedGacksung) {
    setAppliedGacksung(canShowGacksung)
    setImageVariant(canShowGacksung ? 'gacksung' : 'normal')
  }

  const sinner = getSinnerFromId(identityId) as Sinner

  const handleSwapImage = () => {
    setImageVariant((prev: ImageVariant) => (prev === 'gacksung' ? 'normal' : 'gacksung'))
  }

  const currentImagePath = getIdentityDetailImagePath(identityId, imageVariant)

  return (
    <div className="space-y-4">
      <SinnerTitleRow
        sinner={sinner}
        name={name}
        rank={rank}
        rankIconSrc={getRarityIconPath(rank)}
        ringRank={rank}
      />

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
