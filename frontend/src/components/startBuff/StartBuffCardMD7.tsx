import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  getStartBuffIconPath,
  getStartBuffPanePath,
  getStartBuffHighlightPath,
  getStartBuffStarLightPath,
  getStartBuffEnhancementBgPath,
  getStartBuffEnhancementOverlayPath,
  getStartBuffEnhancementIconPath,
} from '@/lib/assetPaths'
import { MD_ACCENT_COLORS } from '@/lib/constants'
import { getDisplayFontForLanguage, getDisplayFontForNumeric } from '@/lib/utils'
import type { StartBuff, StartBuffI18n, BattleKeywords, EnhancementLevel } from '@/types/StartBuffTypes'
import { getEnhancementSuffix, createBuffId } from '@/types/StartBuffTypes'
import { AutoSizeText } from '@/components/common/AutoSizeText'
import { formatBuffEffects } from './formatBuffDescription'

const MD_VERSION = 7

interface StartBuffCardMD7Props {
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
}

/**
 * MD7 start buff card component (edit-only)
 *
 * Enhancement is controlled by parent for batch operation support.
 *
 * Layout:
 * - Top black area: star light + cost (top-right)
 * - Second black area: buff icon (left) + buff name (right)
 * - Center area: description
 * - Bottom: enhancement buttons
 */
export function StartBuffCardMD7({
  buff,
  allBuffs,
  i18n,
  battleKeywords,
  isSelected,
  onSelect,
  enhancement,
  onEnhancementChange,
}: StartBuffCardMD7Props) {
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

  function EnhancementButton({ lvl }: { lvl: 1 | 2 }) {
    const isButtonSelected = enhancement === lvl
    const iconPath = isButtonSelected
      ? getStartBuffEnhancementIconPath(lvl)
      : getStartBuffEnhancementIconPath(0)
    const iconCount = lvl === 2 && !isButtonSelected ? 2 : 1
    return (
      <div className="flex-1 h-7 relative overflow-visible mx-1.25 -translate-x-1">
        <button
          onClick={(e) => { e.stopPropagation(); handleEnhancementClick(lvl) }}
          className="absolute inset-0 overflow-visible"
          style={{
            borderStyle: 'solid',
            borderWidth: '6px',
            borderImageSource: `url('${getStartBuffEnhancementBgPath(0, MD_VERSION)}')`,
            borderImageSlice: '8 fill',
            borderImageOutset: '0px',
            borderImageRepeat: 'stretch',
          }}
        />
        {isButtonSelected && (
          <div
            className="absolute inset-0 pointer-events-none overflow-visible -translate-x-[1.5px] -translate-y-[1px]"
            style={{
              borderStyle: 'solid',
              borderWidth: '8px',
              borderImageSource: `url('${getStartBuffEnhancementOverlayPath(MD_VERSION)}')`,
              borderImageSlice: '16 fill',
              borderImageOutset: '2.5px',
              borderImageRepeat: 'stretch',
            }}
          />
        )}
        <div className="absolute inset-0 flex items-center justify-center gap-0.5 pointer-events-none">
          {Array.from({ length: iconCount }).map((_, i) => (
            <img key={i} src={iconPath} alt=""
              className={`w-auto shrink-0 ${isButtonSelected ? lvl === 2 ? 'h-[20.8px]' : 'h-[16.9px]' : 'h-4'}`}
            />
          ))}
        </div>
      </div>
    )
  }

  return (
    <div
      className={`relative cursor-pointer transition-transform duration-150 ${isPressed ? 'scale-95' : 'scale-100'} `}
      onMouseEnter={() => { setIsHovered(true) }}
      onMouseLeave={() => { setIsHovered(false) }}
      onClick={handleCardClick}
    >
      {/* Pane background */}
      <img
        src={getStartBuffPanePath(MD_VERSION)}
        alt=""
        className="w-66 h-80 object-fill"
      />

      {/* Content overlay */}
      <div className="absolute inset-0 flex flex-col pt-1">
        {/* Top black area: Cost with star (top-right) */}
        <div className="relative" style={{ height: '15%' }}>
          <div className="absolute left-3/4 top-9/32 -translate-x-3 -translate-y-1/2 flex items-center gap-1">
            <img
              src={getStartBuffStarLightPath()}
              alt=""
              className="w-6 h-6 object-contain"
            />
            <span
              className="text-[30px] -translate-y-[3px] "
              style={{ color: enhancement > 0 ? '#f8c200' : undefined, fontFamily: getDisplayFontForNumeric(), textShadow: '1px 1px 1px black' }}
            >
              {displayBuff.cost}
            </span>
          </div>
        </div>

        {/* Second black area: Icon (left) + Name (right) */}
        <div className="flex items-center" style={{ height: '5%' }}>
          {/* Buff icon - upper left */}
          <img
            src={getStartBuffIconPath(buff.baseId, MD_VERSION)}
            alt=""
            className="w-16 h-16 ml-6 object-contain"
          />

          {/* Name */}
          <div className="translate-y-1/8">
            <AutoSizeText
              text={`${displayBuff.name}${getEnhancementSuffix(enhancement)}`}
              width={160}
              minFontSize={12}
              maxFontSize={22}
              className="text-center"
              style={{
                color: MD_ACCENT_COLORS[7],
                textShadow: '2px 2px 1px black',
                ...getDisplayFontForLanguage(i18nInstance.language),
              }}
            />
          </div>
        </div>

        {/* Description - center area */}
        <div className={`flex-1 overflow-y-auto px-2 py-2 m-3 scrollbar-hide text-[${MD_ACCENT_COLORS[7]}] mt-10 mr-5`}>
          <div className="space-y-0.5" style={{ wordBreak: 'keep-all' }}>
            {formatBuffEffects(displayBuff.effects, i18n, battleKeywords)}
          </div>
        </div>

        {/* Enhancement buttons - bottom */}
        <div className="flex px-5.25 pb-5">
          <EnhancementButton lvl={1} />
          <EnhancementButton lvl={2} />
        </div>
      </div>
      {/* Highlight overlay */}
      <img
        src={getStartBuffHighlightPath(MD_VERSION)}
        alt=""
        className={`absolute inset-0 w-67.5 h-81 justify-center -translate-x-0.5 -translate-y-0.25 pointer-events-none transition-opacity duration-200 ${showHighlight ? 'opacity-100' : 'opacity-0'}`}
      />
    </div>
  )
}
