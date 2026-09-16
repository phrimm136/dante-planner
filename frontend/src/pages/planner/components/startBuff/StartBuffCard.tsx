import { Suspense, useEffect, useRef, useState, type CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
import {
  getStartBuffIconPath,
  getStartBuffPanePath,
  getStartBuffHighlightPath,
  getStartBuffStarLightPath,
  getStartBuffEnhancementBgPath,
  getStartBuffEnhancementOverlayPath,
  getStartBuffEnhancementIconPath,
} from '@/shared/assets'
import { ACCENT_COLORS, MD_ACCENT_COLORS } from '@/lib/constants'
import { getDisplayFontForNumeric } from '@/lib/utils'
import { Skeleton } from '@/components/ui/skeleton'
import type { StartBuff, StartBuffI18n, BattleKeywords, EnhancementLevel } from '@/shared/gameText'
import { getEnhancementSuffix, createBuffId } from '@/shared/gameText'
import { formatBuffEffects } from './formatBuffDescription'
import { StartBuffName } from './StartBuffName'
import {
  START_BUFF_CARD,
  START_BUFF_CARD_VARIANTS,
  cqw,
  pct,
  resolveStartBuffCardVersion,
  type StartBuffCardVariant,
} from '../../lib/cardLayout'

type EnhancementStateKey = keyof StartBuffCardVariant['enhancementStates']

/** State an enhancement button is in, keyed by the level it toggles. */
const SELECTED_ENHANCEMENT_STATES: Record<1 | 2, EnhancementStateKey> = {
  1: 'plus1',
  2: 'plus2',
}

function EnhancementButton({
  lvl,
  enhancement,
  variant,
  version,
  onEnhancementClick,
}: {
  lvl: 1 | 2
  enhancement: EnhancementLevel
  variant: StartBuffCardVariant
  version: number
  onEnhancementClick: (level: 1 | 2) => void
}) {
  const { t } = useTranslation('database')
  const isButtonSelected = enhancement === lvl
  const iconPath = isButtonSelected
    ? getStartBuffEnhancementIconPath(lvl)
    : getStartBuffEnhancementIconPath(0)
  const iconCount = lvl === 2 && !isButtonSelected ? 2 : 1
  const stateKey = isButtonSelected ? SELECTED_ENHANCEMENT_STATES[lvl] : 'unselected'
  const border = variant.enhancementStates[stateKey]
  const overlay = variant.enhancementOverlay
  return (
    <div
      className="flex-1 relative overflow-visible"
      style={{
        height: cqw(variant.enhancementSlot.height),
        marginInline: cqw(variant.enhancementSlot.marginX),
        transform: `translateX(${cqw(variant.enhancementSlot.translateX)})`,
      }}
    >
      <button
        onClick={(e) => {
          e.stopPropagation()
          onEnhancementClick(lvl)
        }}
        aria-label={`${t('tierLabel.enhancement')} ${String(lvl)}`}
        aria-pressed={isButtonSelected}
        className="absolute inset-0 overflow-visible"
        style={{
          borderStyle: 'solid',
          borderWidth: cqw(border.width),
          borderImageSource: `url('${getStartBuffEnhancementBgPath(border.bgLevel, version)}')`,
          borderImageSlice: `${String(border.slice)} fill`,
          borderImageOutset: cqw(border.outset),
          borderImageRepeat: 'stretch',
        }}
      />
      {overlay && isButtonSelected && (
        <div
          className="absolute inset-0 pointer-events-none overflow-visible"
          style={{
            transform: `translate(${cqw(overlay.translateX)}, ${cqw(overlay.translateY)})`,
            borderStyle: 'solid',
            borderWidth: cqw(overlay.width),
            borderImageSource: `url('${getStartBuffEnhancementOverlayPath(version)}')`,
            borderImageSlice: `${String(overlay.slice)} fill`,
            borderImageOutset: cqw(overlay.outset),
            borderImageRepeat: 'stretch',
          }}
        />
      )}
      <div
        className="absolute inset-0 flex items-center justify-center pointer-events-none"
        style={{ gap: cqw(START_BUFF_CARD.enhancementIconGap) }}
      >
        {Array.from({ length: iconCount }).map((_, i) => (
          <img
            key={i}
            src={iconPath}
            alt=""
            className="w-auto shrink-0"
            style={{ height: cqw(START_BUFF_CARD.enhancementIcon[stateKey]) }}
          />
        ))}
      </div>
    </div>
  )
}

/** Duration of the card press-down animation */
const PRESS_ANIMATION_MS = 100

interface StartBuffCardProps {
  /** Mirror Dungeon version selecting the card artwork and layout */
  mdVersion: number
  /** The buff to display (contains enhancement level from displayBuffs) */
  buff: StartBuff
  allBuffs: StartBuff[]
  i18n: StartBuffI18n
  battleKeywords?: BattleKeywords
  isSelected: boolean
  onSelect: (buffId: number, selected: boolean) => void
  /** Current enhancement level (controlled by parent) */
  enhancement: EnhancementLevel
  /** Callback when enhancement changes via card's +/++ buttons */
  onEnhancementChange: (baseId: number, level: EnhancementLevel) => void
}

const ROOT_STYLE: CSSProperties = {
  containerType: 'inline-size',
  aspectRatio: START_BUFF_CARD.aspect,
}

/**
 * Start buff card component (edit-only), filling the width its slot gives it.
 *
 * Enhancement is controlled by parent for batch operation support.
 *
 * Layout:
 * - Top black area: star light + cost (top-right)
 * - Second black area: buff icon (left) + buff name (right)
 * - Center area: description
 * - Bottom: enhancement buttons
 */
export function StartBuffCard({
  mdVersion,
  buff,
  allBuffs,
  i18n,
  battleKeywords,
  isSelected,
  onSelect,
  enhancement,
  onEnhancementChange,
}: StartBuffCardProps) {
  const [isHovered, setIsHovered] = useState(false)

  const version = resolveStartBuffCardVersion(mdVersion)
  const variant = START_BUFF_CARD_VARIANTS[version]

  // Show highlight on selection or hover
  const showHighlight = isSelected || isHovered

  // Get the buff data for current enhancement level
  const currentBuffId = createBuffId(buff.baseId, enhancement)
  const displayBuff = allBuffs.find((b) => Number(b.id) === currentBuffId) ?? buff

  // Enhancement button click: toggle enhancement via parent
  const handleEnhancementClick = (level: 1 | 2) => {
    const newEnhancement: EnhancementLevel = enhancement === level ? 0 : level
    onEnhancementChange(buff.baseId, newEnhancement)
  }

  // Press animation state
  const [isPressed, setIsPressed] = useState(false)
  const pressTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Clear the press-animation timer on unmount so it cannot fire after teardown
  useEffect(() => {
    return () => {
      if (pressTimeoutRef.current !== null) clearTimeout(pressTimeoutRef.current)
    }
  }, [])

  // Card click: toggle selection with current enhancement
  const handleCardClick = () => {
    // Trigger press animation
    setIsPressed(true)
    if (pressTimeoutRef.current !== null) clearTimeout(pressTimeoutRef.current)
    pressTimeoutRef.current = setTimeout(() => {
      setIsPressed(false)
    }, PRESS_ANIMATION_MS)

    onSelect(currentBuffId, !isSelected)
  }

  const nameText = `${displayBuff.name}${getEnhancementSuffix(enhancement)}`

  return (
    <div
      className={`relative w-full cursor-pointer transition-transform duration-150 ${isPressed ? 'scale-95' : 'scale-100'}`}
      style={ROOT_STYLE}
      onMouseEnter={() => {
        setIsHovered(true)
      }}
      onMouseLeave={() => {
        setIsHovered(false)
      }}
    >
      {/* Pane background */}
      <img
        src={getStartBuffPanePath(version)}
        alt=""
        style={{
          width: pct(variant.pane.width),
          height: '100%',
          objectFit: variant.pane.fit,
        }}
      />

      {/* Content overlay */}
      <div
        className="absolute inset-0 flex flex-col"
        style={{ paddingTop: cqw(START_BUFF_CARD.contentPaddingTop) }}
      >
        {/* Top black area: Cost with star (top-right) */}
        <div className="relative" style={{ height: pct(START_BUFF_CARD.costRowHeight) }}>
          <div
            className="absolute flex items-center"
            style={{
              left: pct(variant.cost.left),
              top: pct(variant.cost.top),
              gap: cqw(START_BUFF_CARD.costGap),
              transform: `translate(${cqw(variant.cost.translateX)}, -50%)`,
            }}
          >
            <img
              src={getStartBuffStarLightPath()}
              alt=""
              className="object-contain"
              style={{
                width: cqw(START_BUFF_CARD.starIcon),
                height: cqw(START_BUFF_CARD.starIcon),
              }}
            />
            <span
              style={{
                fontSize: cqw(variant.cost.fontSize),
                transform: `translateY(-${cqw(variant.cost.lift)})`,
                color: enhancement > 0 ? ACCENT_COLORS.ENHANCED : undefined,
                fontFamily: getDisplayFontForNumeric(),
                ...(variant.cost.shadow !== undefined && { textShadow: variant.cost.shadow }),
              }}
            >
              {displayBuff.cost}
            </span>
          </div>
        </div>

        {/* Second black area: Icon (left) + Name (right) */}
        <div className="flex items-center" style={{ height: pct(variant.nameRowHeight) }}>
          {/* Buff icon - upper left */}
          <img
            src={getStartBuffIconPath(buff.baseId, version)}
            alt=""
            className="object-contain shrink-0"
            style={{
              width: cqw(variant.buffIcon.size),
              height: cqw(variant.buffIcon.size),
              marginLeft: cqw(variant.buffIcon.marginLeft),
            }}
          />

          {/* Name */}
          <div
            className="overflow-hidden"
            style={{
              width: cqw(START_BUFF_CARD.nameWidth),
              marginLeft: cqw(variant.name.marginLeft),
              transform: `translateY(${pct(variant.name.translateYSelf)})`,
            }}
          >
            <Suspense fallback={<Skeleton className="h-5 w-full" />}>
              <StartBuffName
                text={nameText}
                maxSize={variant.name.maxSize}
                color={MD_ACCENT_COLORS[version]}
                shadow={variant.name.shadow}
              />
            </Suspense>
          </div>
        </div>

        {/* Description - center area */}
        <div
          role="presentation"
          className="relative z-20 flex-1 overflow-y-auto scrollbar-hide"
          style={{
            paddingInline: cqw(variant.description.paddingX),
            paddingBlock: cqw(variant.description.paddingY),
            margin: cqw(variant.description.margin),
            marginTop: cqw(variant.description.marginTop),
            marginRight: cqw(variant.description.marginRight),
            fontSize: cqw(START_BUFF_CARD.effectFontSize),
            lineHeight: cqw(START_BUFF_CARD.effectLineHeight),
            ...(variant.description.color !== undefined && { color: variant.description.color }),
          }}
          onClick={handleCardClick}
        >
          <div
            className="flex flex-col"
            style={{ wordBreak: 'keep-all', gap: cqw(START_BUFF_CARD.effectGap) }}
          >
            {formatBuffEffects(displayBuff.effects, i18n, battleKeywords)}
          </div>
        </div>

        {/* Enhancement buttons - bottom */}
        <div
          className="relative z-20 flex"
          style={{
            gap: cqw(variant.enhancementRow.gap),
            paddingInline: cqw(variant.enhancementRow.paddingX),
            paddingBottom: cqw(variant.enhancementRow.paddingBottom),
          }}
        >
          <EnhancementButton
            lvl={1}
            enhancement={enhancement}
            variant={variant}
            version={version}
            onEnhancementClick={handleEnhancementClick}
          />
          <EnhancementButton
            lvl={2}
            enhancement={enhancement}
            variant={variant}
            version={version}
            onEnhancementClick={handleEnhancementClick}
          />
        </div>
      </div>

      {/* Card click target */}
      <button
        type="button"
        className="absolute inset-0 z-10"
        aria-label={displayBuff.name}
        aria-pressed={isSelected}
        onClick={handleCardClick}
      />

      {/* Highlight overlay */}
      <img
        src={getStartBuffHighlightPath(version)}
        alt=""
        className={`absolute inset-0 z-30 pointer-events-none transition-opacity duration-200 ${showHighlight ? 'opacity-100' : 'opacity-0'}`}
        style={{
          width: pct(variant.highlight.width),
          height: pct(variant.highlight.height),
          transform: `translate(${cqw(variant.highlight.translateX)}, ${cqw(variant.highlight.translateY)})`,
        }}
      />
    </div>
  )
}
