import { Suspense, type CSSProperties, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import {
  getEGOCardFramePath,
  getEGOHoverRingPath,
  getEGOImagePath,
  getEGOMaskPath,
  getEGONameBgPath,
  getEGOCardGradePath,
  getEGOCardThreadspinPath,
  getEGORankIconPath,
  getSinnerFacePath,
  getEGOIconRingPath,
} from '@/shared/assets'
import { aspectOf, layerStyle, pctStyle } from '@/shared/cardLayout'
import { getSinnerFromId } from '@/shared/gameData'
import { cn } from '@/lib/utils'
import { EGO_GEOMETRY } from '../lib/cardLayout'
import { Skeleton } from '@/components/ui/skeleton'
import type { EGOListItem } from '../types/EGOTypes'
import {
  EGO_CARD_BADGE_SKEW,
  EGO_CARD_BADGE_SKEW_ORIGIN,
  EGO_CARD_LAYERS,
  EGO_CARD_ROOT_SCALE,
  EGO_HOVER_RING_BRIGHTNESS,
  EGO_NAME_RECT,
  egoPortraitWindowStyle,
} from '../lib/cardLayout'
import { EGOName } from './EGOName'

interface EGOCardProps {
  /** The EGO data to display */
  ego: EGOListItem
  /** Show the ring at full brightness (selected state) */
  isSelected?: boolean
  /** Custom overlay content (e.g., selected indicator) */
  overlay?: ReactNode
  /** Additional CSS classes for styling flexibility */
  className?: string
}

/**
 * View-only EGO card in the game's geometry.
 *
 * Carries no interaction of its own; a parent wraps it in a `Link`, a button, or a trigger.
 */
export function EGOCard({ ego, isSelected = false, overlay, className }: EGOCardProps) {
  const { t } = useTranslation(['common', 'database'])
  const { id, egoType: rank, attributeTypes, maxThreadspin } = ego
  const [primaryAttributeType] = attributeTypes
  const sinner = getSinnerFromId(id)
  const maskPath = getEGOMaskPath()

  const rootStyle: CSSProperties = {
    containerType: 'inline-size',
    aspectRatio: aspectOf(EGO_GEOMETRY.size),
    transform: `scale(${String(EGO_CARD_ROOT_SCALE)})`,
    '--ego-hover-ring-brightness': EGO_HOVER_RING_BRIGHTNESS,
  } as CSSProperties

  return (
    <div className={cn('group relative w-full', className)} style={rootStyle}>
      <img
        src={maskPath}
        alt=""
        loading="lazy"
        style={layerStyle(EGO_CARD_LAYERS.portraitWindow)}
        className="pointer-events-none"
      />
      <div data-testid="ego-portrait-window" style={egoPortraitWindowStyle(maskPath)}>
        <img
          src={getEGOImagePath(id)}
          alt=""
          loading="lazy"
          className="absolute inset-0 w-full h-full object-cover"
        />
      </div>

      <img
        src={getEGOCardFramePath()}
        alt={t('a11y.egoFrame')}
        loading="lazy"
        style={layerStyle(EGO_CARD_LAYERS.frame)}
        className="pointer-events-none"
      />

      <img
        src={getEGOHoverRingPath()}
        alt=""
        loading="lazy"
        style={layerStyle(EGO_CARD_LAYERS.hoverRing)}
        className={cn(
          'pointer-events-none',
          isSelected
            ? 'opacity-100'
            : 'opacity-0 group-hover:opacity-100 group-active:opacity-100 group-hover:brightness-[var(--ego-hover-ring-brightness)] group-active:brightness-[var(--ego-hover-ring-brightness)]',
        )}
      />

      <img
        src={getEGONameBgPath(primaryAttributeType)}
        alt={t('a11y.infoPanel')}
        loading="lazy"
        style={layerStyle(EGO_CARD_LAYERS.namePlate)}
        className="pointer-events-none"
      />

      <Suspense
        fallback={
          <Skeleton style={pctStyle(EGO_NAME_RECT)} className="pointer-events-none bg-foreground" />
        }
      >
        <EGOName id={id} />
      </Suspense>

      <img
        src={getEGOCardThreadspinPath(maxThreadspin)}
        alt={`${t('database:filters.tier')} ${String(maxThreadspin)}`}
        loading="lazy"
        style={{
          ...layerStyle(EGO_CARD_LAYERS.threadspinBadge),
          transform: `skewY(${String(EGO_CARD_BADGE_SKEW.threadspin)}deg)`,
          transformOrigin: EGO_CARD_BADGE_SKEW_ORIGIN.threadspin,
        }}
        className="pointer-events-none"
      />

      <img
        src={getEGOCardGradePath(rank)}
        alt={rank}
        loading="lazy"
        style={{
          ...layerStyle(EGO_CARD_LAYERS.gradeBadge),
          transform: `skewY(${String(EGO_CARD_BADGE_SKEW.grade)}deg)`,
          transformOrigin: EGO_CARD_BADGE_SKEW_ORIGIN.grade,
        }}
        className="pointer-events-none"
      />

      <img
        src={getEGORankIconPath(rank)}
        alt=""
        loading="lazy"
        style={layerStyle(EGO_CARD_LAYERS.typeLabel)}
        className="pointer-events-none"
      />

      <img
        src={getEGOIconRingPath()}
        alt={t('a11y.sinnerBackground')}
        loading="lazy"
        style={layerStyle(EGO_CARD_LAYERS.iconRing)}
        className="pointer-events-none"
      />

      <img
        src={getSinnerFacePath(sinner)}
        alt={sinner}
        loading="lazy"
        style={layerStyle(EGO_CARD_LAYERS.face)}
        className="pointer-events-none"
      />

      {overlay}
    </div>
  )
}
