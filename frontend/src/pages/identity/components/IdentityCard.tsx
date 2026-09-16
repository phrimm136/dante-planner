import { Suspense, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import type { IdentityListItem } from '../types/IdentityTypes'
import { Skeleton } from '@/components/ui/skeleton'
import { IdentityName } from './IdentityName'
import {
  getIdentityInfoImagePath,
  getIdentityImageFallbackPath,
  getIdentityMaskPath,
  getUptieFramePath,
  getIdentityHoverRingPath,
  getSinnerIconRingPath,
  getSinnerFacePath,
  getIdentityGradePath,
} from '@/shared/assets'
import { MAX_LEVEL, getSinnerFromId } from '@/shared/gameData'
import { layerStyle } from '@/shared/cardLayout'
import { cn, getDisplayFontForNumeric } from '@/lib/utils'
import {
  IDENTITY_CARD_LAYERS,
  cardRootStyle,
  levelStyle,
  nameBlockStyle,
  portraitStyle,
  portraitWindowStyle,
} from '../lib/cardLayout'

interface IdentityCardProps {
  identity: IdentityListItem
  /** Override uptie level for image display (uses gacksung at 3+) */
  uptie?: number
  /** Override level display (defaults to MAX_LEVEL) */
  level?: number
  /** Dim the entire card (selected/equipped state) */
  isSelected?: boolean
  /** What the card's graphics multiply by, as `PersonalitySlotGraphics.SetColor` writes it */
  dim?: number | undefined
  /** Custom overlay content (e.g., selected indicator, deployment badge) */
  overlay?: ReactNode
  /** Additional CSS classes */
  className?: string
}

/**
 * View-only identity card. It fills the width its parent gives it and carries no
 * interaction; wrap it in a Link, button or trigger for that.
 *
 * @example
 * <div style={{ width: IDENTITY_GEOMETRY.size.widthPx }}>
 *   <IdentityCard identity={identity} />
 * </div>
 */
export function IdentityCard({
  identity,
  uptie = 4,
  level = MAX_LEVEL,
  isSelected = false,
  dim = 1,
  overlay,
  className,
}: IdentityCardProps) {
  const { t } = useTranslation()
  const { id, rank } = identity
  const sinner = getSinnerFromId(id)

  return (
    <div className={cn('group', className)} style={cardRootStyle()}>
      <div
        data-testid="identity-card-graphics"
        className={cn('absolute inset-0', isSelected && 'brightness-50')}
        style={dim === 1 ? undefined : { filter: `brightness(${String(dim)})` }}
      >
        <div
          data-testid="identity-portrait-window"
          style={portraitWindowStyle(getIdentityMaskPath())}
        >
          <img
            src={getIdentityInfoImagePath(id, uptie)}
            onError={(e) => {
              const target = e.currentTarget
              if (!target.dataset.fallback) {
                target.dataset.fallback = 'true'
                target.src = getIdentityImageFallbackPath(id)
              }
            }}
            alt={identity.name}
            loading="lazy"
            style={portraitStyle()}
          />
        </div>

        <img
          src={getUptieFramePath(rank, uptie)}
          alt={`${String(rank)} star frame`}
          loading="lazy"
          className="pointer-events-none"
          style={layerStyle(IDENTITY_CARD_LAYERS.frame)}
        />

        <img
          src={getIdentityGradePath(rank)}
          alt={String(rank)}
          loading="lazy"
          className="pointer-events-none"
          style={layerStyle(IDENTITY_CARD_LAYERS.grade)}
        />

        <div
          data-testid="identity-name-block"
          className="pointer-events-none"
          style={nameBlockStyle()}
        >
          <div
            data-testid="identity-level"
            style={{ ...levelStyle(), fontFamily: getDisplayFontForNumeric() }}
          >
            {`Lv. ${String(level)}`}
          </div>

          <Suspense
            fallback={
              <span className="flex flex-col items-end gap-0.5">
                <Skeleton className="w-14 h-2.5 bg-white/30" />
                <Skeleton className="w-10 h-2.5 bg-white/30" />
              </span>
            }
          >
            <IdentityName id={id} />
          </Suspense>
        </div>

        <img
          src={getIdentityHoverRingPath(rank, uptie)}
          alt=""
          loading="lazy"
          className="pointer-events-none opacity-0 group-hover:opacity-100 group-active:opacity-100"
          style={layerStyle(IDENTITY_CARD_LAYERS.hoverRing)}
        />

        <img
          src={getSinnerIconRingPath(rank)}
          alt={t('a11y.sinnerBackground')}
          loading="lazy"
          className="pointer-events-none"
          style={layerStyle(IDENTITY_CARD_LAYERS.iconRing)}
        />

        <img
          src={getSinnerFacePath(sinner)}
          alt={sinner}
          loading="lazy"
          className="pointer-events-none"
          style={layerStyle(IDENTITY_CARD_LAYERS.face)}
        />
      </div>

      {overlay}
    </div>
  )
}
