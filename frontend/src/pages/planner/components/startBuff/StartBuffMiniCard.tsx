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
  /** Full buff ID including enhancement (e.g., 101, 202, 303) */
  buffId: number
  /** Localized display name (e.g., "Starlight of Eden") */
  displayName: string
  /** Mirror Dungeon version (from usePlannerConfig) */
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

/**
 * Compact summary card for a selected start buff, filling the width its slot gives it.
 * Shows buff icon (upper half), name with enhancement suffix (lower half),
 * enhancement indicator (top-right), and hover highlight overlay.
 */
export function StartBuffMiniCard({ buffId, displayName, mdVersion }: StartBuffMiniCardProps) {
  const baseId = getBaseIdFromBuffId(buffId)
  const enhancement = getEnhancementFromBuffId(buffId)
  const suffix = getEnhancementSuffix(enhancement)
  const accentColor = MD_ACCENT_COLORS[mdVersion]

  return (
    <div className="group relative w-full" style={ROOT_STYLE}>
      {/* Background image */}
      <img
        src={getStartBuffMiniPath(mdVersion)}
        alt=""
        className="absolute inset-0 w-full h-full object-contain"
      />

      {/* Content container - flex column for vertical layout */}
      <div
        className="absolute inset-0 flex flex-col"
        style={{ gap: cqw(START_BUFF_MINI_CARD.rowGap) }}
      >
        {/* Upper half: Buff icon (centered) */}
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

        {/* Lower half: Name + enhancement suffix */}
        <div
          className="flex-1 flex items-center justify-center overflow-hidden"
          style={{ paddingInline: cqw(START_BUFF_MINI_CARD.namePaddingX) }}
        >
          <Suspense fallback={<Skeleton className="h-5 w-full" />}>
            <StartBuffMiniName text={`${displayName}${suffix}`} color={accentColor} />
          </Suspense>
        </div>
      </div>

      {/* Enhancement indicator - top-right */}
      <div
        style={{
          transform: `scale(${String(START_BUFF_MINI_CARD.enhancementScale)}) translate(${cqw(START_BUFF_MINI_CARD.enhancementTranslateX)}, ${cqw(START_BUFF_MINI_CARD.enhancementTranslateY)})`,
        }}
      >
        <EGOGiftEnhancementIndicator enhancement={enhancement} />
      </div>

      {/* Hover overlay */}
      <img
        src={getStartBuffMiniHighlightPath(mdVersion)}
        alt=""
        className="absolute inset-0 w-full h-full object-contain pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity"
      />
    </div>
  )
}
