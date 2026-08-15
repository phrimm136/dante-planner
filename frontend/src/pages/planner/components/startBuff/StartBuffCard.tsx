import { useEffect, useRef, useState } from 'react'
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
import { MD_ACCENT_COLORS } from '@/lib/constants'
import { getDisplayFontForLanguage, getDisplayFontForNumeric } from '@/lib/utils'
import type { StartBuff, StartBuffI18n, BattleKeywords, EnhancementLevel } from '@/shared/gameText'
import { getEnhancementSuffix, createBuffId } from '@/shared/gameText'
import { AutoSizeText } from '@/components/ui/AutoSizeText'
import { formatBuffEffects } from './formatBuffDescription'
import { ACCENT_COLORS } from '@/lib/constants'
import { CARD_VARIANTS, resolveStartBuffCardVersion } from './startBuffCardVariants'
import type { StartBuffCardVariant } from './startBuffCardVariants'

type EnhancementStateKey = keyof StartBuffCardVariant['enhancementStates']

/** State an enhancement button is in, keyed by the level it toggles. */
const SELECTED_ENHANCEMENT_STATES: Record<1 | 2, EnhancementStateKey> = {
  1: 'plus1',
  2: 'plus2',
}

/** Icon height per enhancement-button state. */
const ENHANCEMENT_ICON_HEIGHTS: Record<EnhancementStateKey, string> = {
  unselected: 'h-4',
  plus1: 'h-[16.9px]',
  plus2: 'h-[20.8px]',
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
  const isButtonSelected = enhancement === lvl
  const iconPath = isButtonSelected
    ? getStartBuffEnhancementIconPath(lvl)
    : getStartBuffEnhancementIconPath(0)
  const iconCount = lvl === 2 && !isButtonSelected ? 2 : 1
  const stateKey = isButtonSelected ? SELECTED_ENHANCEMENT_STATES[lvl] : 'unselected'
  const border = variant.enhancementStates[stateKey]
  const overlay = variant.enhancementOverlay
  return (
    <div className={variant.enhancementSlot}>
      <button
        onClick={(e) => {
          e.stopPropagation()
          onEnhancementClick(lvl)
        }}
        className="absolute inset-0 overflow-visible"
        style={{
          borderStyle: 'solid',
          borderWidth: `${border.width}px`,
          borderImageSource: `url('${getStartBuffEnhancementBgPath(border.bgLevel, version)}')`,
          borderImageSlice: `${border.slice} fill`,
          borderImageOutset: `${border.outset}px`,
          borderImageRepeat: 'stretch',
        }}
      />
      {overlay && isButtonSelected && (
        <div
          className={overlay.className}
          style={{
            borderStyle: 'solid',
            borderWidth: `${overlay.width}px`,
            borderImageSource: `url('${getStartBuffEnhancementOverlayPath(version)}')`,
            borderImageSlice: `${overlay.slice} fill`,
            borderImageOutset: `${overlay.outset}px`,
            borderImageRepeat: 'stretch',
          }}
        />
      )}
      <div className="absolute inset-0 flex items-center justify-center gap-0.5 pointer-events-none">
        {Array.from({ length: iconCount }).map((_, i) => (
          <img
            key={i}
            src={iconPath}
            alt=""
            className={`w-auto shrink-0 ${ENHANCEMENT_ICON_HEIGHTS[stateKey]}`}
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

/**
 * Start buff card component (edit-only)
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
  const { i18n: i18nInstance } = useTranslation()
  const [isHovered, setIsHovered] = useState(false)

  const version = resolveStartBuffCardVersion(mdVersion)
  const variant = CARD_VARIANTS[version]

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

  return (
    <div
      className={`relative cursor-pointer ${variant.root ? `${variant.root} ` : ''}transition-transform duration-150 ${isPressed ? 'scale-95' : 'scale-100'} `}
      onMouseEnter={() => {
        setIsHovered(true)
      }}
      onMouseLeave={() => {
        setIsHovered(false)
      }}
      onClick={handleCardClick}
    >
      {/* Pane background */}
      <img src={getStartBuffPanePath(version)} alt="" className={variant.pane} />

      {/* Content overlay */}
      <div className="absolute inset-0 flex flex-col pt-1">
        {/* Top black area: Cost with star (top-right) */}
        <div className="relative" style={{ height: '15%' }}>
          <div className={variant.costAnchor}>
            <img src={getStartBuffStarLightPath()} alt="" className="w-6 h-6 object-contain" />
            <span
              className={variant.costText}
              style={{
                color: enhancement > 0 ? ACCENT_COLORS.ENHANCED : undefined,
                fontFamily: getDisplayFontForNumeric(),
                textShadow: variant.costTextShadow,
              }}
            >
              {displayBuff.cost}
            </span>
          </div>
        </div>

        {/* Second black area: Icon (left) + Name (right) */}
        <div className="flex items-center" style={{ height: variant.nameRowHeight }}>
          {/* Buff icon - upper left */}
          <img
            src={getStartBuffIconPath(buff.baseId, version)}
            alt=""
            className={variant.buffIcon}
          />

          {/* Name */}
          <div className={variant.nameWrapper}>
            <AutoSizeText
              text={`${displayBuff.name}${getEnhancementSuffix(enhancement)}`}
              width={160}
              minFontSize={12}
              maxFontSize={variant.nameMaxFontSize}
              className="text-center"
              style={{
                color: MD_ACCENT_COLORS[version],
                textShadow: variant.nameTextShadow,
                ...getDisplayFontForLanguage(i18nInstance.language),
              }}
            />
          </div>
        </div>

        {/* Description - center area */}
        <div className={variant.description} style={{ color: variant.descriptionColor }}>
          <div className="space-y-0.5" style={{ wordBreak: 'keep-all' }}>
            {formatBuffEffects(displayBuff.effects, i18n, battleKeywords)}
          </div>
        </div>

        {/* Enhancement buttons - bottom */}
        <div className={variant.enhancementRow}>
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
      {/* Highlight overlay */}
      <img
        src={getStartBuffHighlightPath(version)}
        alt=""
        className={`${variant.highlight} ${showHighlight ? 'opacity-100' : 'opacity-0'}`}
      />
    </div>
  )
}
