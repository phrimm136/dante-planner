import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { getDisplayFontForLanguage } from '@/lib/utils'
import { EGO_GIFT_GEOMETRY, aspectOf } from '@/shared/cardLayout'
import { Skeleton } from '@/components/ui/skeleton'
import {
  getEnhancementFromBuffId,
  getBaseIdFromBuffId,
  getEnhancementSuffix,
} from '@/shared/gameText'
import { useStartBuffMiniCardLayout } from '../../hooks/useStartBuffCardLayout'
import { useStoreSeason } from '../../stores/usePlannerStore'
import {
  fitSpace,
  isShown,
  levelIconFile,
  spriteFile,
  type CardState,
} from '../../lib/startBuffCardLayout'
import { CardSprite } from './CardSprite'
import { CardText } from './StartBuffName'

interface StartBuffMiniCardProps {
  buffId: number
  displayName: string
}

const SLOT = EGO_GIFT_GEOMETRY.size

export function StartBuffMiniCard(props: StartBuffMiniCardProps) {
  return (
    <Suspense fallback={<Skeleton className="w-full h-full" />}>
      <StartBuffMiniCardBody {...props} />
    </Suspense>
  )
}

function StartBuffMiniCardBody({ buffId, displayName }: StartBuffMiniCardProps) {
  const { i18n } = useTranslation()
  const { contentVersion } = useStoreSeason()
  const layout = useStartBuffMiniCardLayout(contentVersion)
  const [hovered, setHovered] = useState(false)

  const baseId = getBaseIdFromBuffId(buffId)
  const enhancement = getEnhancementFromBuffId(buffId)
  const space = fitSpace({ x: 0, y: 0, ...layout.root }, SLOT)
  const state: CardState = {
    selected: false,
    hovered,
    hoveredButton: null,
    enhancement,
    overflows: false,
  }

  return (
    <div
      className="relative w-full"
      style={{ aspectRatio: String(aspectOf(SLOT)) }}
      onMouseEnter={() => {
        setHovered(true)
      }}
      onMouseLeave={() => {
        setHovered(false)
      }}
    >
      {layout.elements
        .filter((element) => isShown(element, state))
        .map((element) => {
          const key = element.path
          if (element.role === 'name') {
            return (
              <CardText
                key={key}
                text={`${displayName}${getEnhancementSuffix(enhancement)}`}
                element={element}
                tableRole="name"
                language={i18n.language}
                space={space}
                color={element.text.color}
                fontFamily={getDisplayFontForLanguage(i18n.language).fontFamily}
                testId="start-buff-mini-name"
              />
            )
          }
          if (!('image' in element)) return null
          const file =
            element.role === 'level'
              ? levelIconFile(layout, enhancement)
              : spriteFile(element.image, baseId)
          if (file === null) return null
          return (
            <CardSprite
              key={key}
              version={contentVersion}
              file={file}
              image={element.image}
              rect={element.rect}
              space={space}
            />
          )
        })}
    </div>
  )
}
