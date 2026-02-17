import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  getStartBuffIconPath,
  getStartBuffPanePath,
  getStartBuffHighlightPath,
  getStartBuffStarLightPath,
} from '@/lib/assetPaths'
import { MD_ACCENT_COLORS, type MDVersion } from '@/lib/constants'
import { getDisplayFontForLanguage, getDisplayFontForNumeric } from '@/lib/utils'
import type { StartBuff, StartBuffI18n, BattleKeywords, EnhancementLevel } from '@/types/StartBuffTypes'
import { getEnhancementSuffix, createBuffId } from '@/types/StartBuffTypes'
import { AutoSizeText } from '@/components/common/AutoSizeText'
import { EnhancementButton } from './EnhancementButton'
import { formatBuffEffects } from './formatBuffDescription'

interface StartBuffCardProps {
  /** The buff to display (contains enhancement level from displayBuffs) */
  buff: StartBuff
  allBuffs: StartBuff[]
  i18n: StartBuffI18n
  battleKeywords?: BattleKeywords
  isSelected: boolean
  onSelect: (buffId: number) => void
  /** Current enhancement level (controlled by parent) */
  enhancement: EnhancementLevel
  /** Callback when enhancement changes via card's +/++ buttons */
  onEnhancementChange: (baseId: number, level: EnhancementLevel) => void
  /** Mirror Dungeon version for accent color */
  mdVersion: MDVersion
}

/**
 * Individual start buff card component (edit-only)
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
  buff,
  allBuffs,
  i18n,
  battleKeywords,
  isSelected,
  onSelect,
  enhancement,
  onEnhancementChange,
  mdVersion,
}: StartBuffCardProps) {
  const { i18n: i18nInstance } = useTranslation()
  const [isHovered, setIsHovered] = useState(false)

  // Show highlight on selection or hover
  const showHighlight = isSelected || isHovered

  // Get the buff data for current enhancement level
  const currentBuffId = createBuffId(buff.baseId, enhancement)
  const displayBuff = allBuffs.find(b => Number(b.id) === currentBuffId) ?? buff

  // Enhancement button click: toggle enhancement via parent
  const handleEnhancementClick = (level: 1 | 2) => {
    const newEnhancement: EnhancementLevel = enhancement === level ? 0 : level
    onEnhancementChange(buff.baseId, newEnhancement)
  }

  // Press animation state
  const [isPressed, setIsPressed] = useState(false)

  // Card click: toggle selection with current enhancement
  const handleCardClick = () => {
    // Trigger press animation
    setIsPressed(true)
    setTimeout(() => { setIsPressed(false) }, 100)

    if (isSelected) {
      // Deselect - signal with negative ID
      onSelect(-currentBuffId)
    } else {
      // Select with current preview enhancement
      onSelect(currentBuffId)
    }
  }

  return (
    <div
      className={`relative cursor-pointer w-68 h-80 transition-transform duration-150 ${isPressed ? 'scale-95' : 'scale-100'} `}
      onMouseEnter={() => { setIsHovered(true) }}
      onMouseLeave={() => { setIsHovered(false) }}
      onClick={handleCardClick}
    >
      {/* Pane background */}
      <img
        src={getStartBuffPanePath()}
        alt=""
        className="w-full h-full object-cover"
      />

      {/* Content overlay */}
      <div className="absolute inset-0 flex flex-col pt-1">
        {/* Top black area: Cost with star (top-right) */}
        <div className="relative" style={{ height: '15%' }}>
          <div className="absolute left-21/32 top-5/8 -translate-y-1/2 flex items-center gap-1">
            <img
              src={getStartBuffStarLightPath()}
              alt=""
              className="w-6 h-6 object-contain"
            />
            <span
              className="text-[25px] -translate-y-1"
              style={{ color: enhancement > 0 ? '#f8c200' : undefined, fontFamily: getDisplayFontForNumeric() }}
            >
              {displayBuff.cost}
            </span>
          </div>
        </div>

        {/* Second black area: Icon (left) + Name (right) */}
        <div className="flex items-center" style={{ height: '12%' }}>
          {/* Buff icon - upper left */}
          <img
            src={getStartBuffIconPath(buff.baseId)}
            alt=""
            className="w-14 h-14 ml-8 object-contain"
          />

          {/* Name */}
          <div className="ml-1">
            <AutoSizeText
              text={`${displayBuff.name}${getEnhancementSuffix(enhancement)}`}
              width={160}
              minFontSize={12}
              maxFontSize={20}
              className="text-center"
              style={{
                color: MD_ACCENT_COLORS[mdVersion],
                ...getDisplayFontForLanguage(i18nInstance.language),
              }}
            />
          </div>
        </div>

        {/* Description - center area */}
        <div className="flex-1 overflow-y-auto px-3 py-2 m-3.5 scrollbar-hide">
          <div className="space-y-0.5" style={{ wordBreak: 'keep-all' }}>
            {formatBuffEffects(displayBuff.effects, i18n, battleKeywords)}
          </div>
        </div>

        {/* Enhancement buttons - bottom */}
        <div className="flex gap-2 px-7 pb-8">
          <EnhancementButton
            level={1}
            isSelected={enhancement === 1}
            onClick={() => { handleEnhancementClick(1) }}
          />
          <EnhancementButton
            level={2}
            isSelected={enhancement === 2}
            onClick={() => { handleEnhancementClick(2) }}
          />
        </div>
      </div>
      {/* Highlight overlay */}
      <img
        src={getStartBuffHighlightPath()}
        alt=""
        className={`absolute inset-0 w-66 h-78 justify-center translate-x-1 translate-y-1.75 pointer-events-none transition-opacity duration-200 ${showHighlight ? 'opacity-100' : 'opacity-0'}`}
      />
    </div>
  )
}
