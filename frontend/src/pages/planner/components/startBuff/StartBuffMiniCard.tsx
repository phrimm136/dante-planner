import { Suspense, type CSSProperties } from 'react'

import {
  getStartBuffIconPath,
  getStartBuffMiniPath,
  getStartBuffMiniHighlightPath,
} from '@/shared/assets'
import { MD_ACCENT_COLORS } from '@/lib/constants'
import { EGO_GIFT_GEOMETRY, aspectOf } from '@/shared/cardLayout'
import { Skeleton } from '@/components/ui/skeleton'
import {
  getEnhancementFromBuffId,
  getBaseIdFromBuffId,
  getEnhancementSuffix,
} from '@/shared/gameText'
import { EGOGiftEnhancementIndicator } from '@/pages/egoGift'
import { START_BUFF_MINI_CARD, cqw, pct } from '../../lib/cardLayout'
import { StartBuffMiniName } from './StartBuffName'

interface StartBuffMiniCardProps {
  buffId: number
  displayName: string
  mdVersion: number
}

const ROOT_STYLE: CSSProperties = {
  containerType: 'inline-size',
  aspectRatio: aspectOf(EGO_GIFT_GEOMETRY.size),
}

const ICON_STYLE: CSSProperties = {
  width: pct(START_BUFF_MINI_CARD.icon),
  height: pct(START_BUFF_MINI_CARD.icon),
}

export function StartBuffMiniCard({ buffId, displayName, mdVersion }: StartBuffMiniCardProps) {
  const baseId = getBaseIdFromBuffId(buffId)
  const enhancement = getEnhancementFromBuffId(buffId)
  const suffix = getEnhancementSuffix(enhancement)
  const accentColor = MD_ACCENT_COLORS[mdVersion]

  return (
    <div className="group relative w-full" style={ROOT_STYLE}>
      <img
        src={getStartBuffMiniPath(mdVersion)}
        alt=""
        className="absolute inset-0 w-full h-full object-contain"
      />

      <div
        className="absolute inset-0 flex flex-col"
        style={{ gap: cqw(START_BUFF_MINI_CARD.rowGap) }}
      >
        <div
          className="flex-1 flex items-center justify-center"
          style={{ paddingTop: cqw(START_BUFF_MINI_CARD.iconPaddingTop) }}
        >
          <img
            src={getStartBuffIconPath(baseId, mdVersion)}
            alt=""
            className="object-contain"
            style={ICON_STYLE}
          />
        </div>

        <div
          className="flex-1 flex items-center justify-center overflow-hidden"
          style={{ paddingInline: cqw(START_BUFF_MINI_CARD.namePaddingX) }}
        >
          <Suspense fallback={<Skeleton className="h-5 w-full" />}>
            <StartBuffMiniName text={`${displayName}${suffix}`} color={accentColor} />
          </Suspense>
        </div>
      </div>

      <div
        style={{
          transform: `scale(${String(START_BUFF_MINI_CARD.enhancementScale)}) translate(${cqw(START_BUFF_MINI_CARD.enhancementTranslateX)}, ${cqw(START_BUFF_MINI_CARD.enhancementTranslateY)})`,
        }}
      >
        <EGOGiftEnhancementIndicator enhancement={enhancement} />
      </div>

      <img
        src={getStartBuffMiniHighlightPath(mdVersion)}
        alt=""
        className="absolute inset-0 w-full h-full object-contain pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity"
      />
    </div>
  )
}
