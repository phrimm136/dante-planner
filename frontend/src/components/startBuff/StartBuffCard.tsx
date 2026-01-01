import { useState } from 'react'
import {
  getStartBuffIconPath,
  getStartBuffPanePath,
  getStartBuffHighlightPath,
  getStartBuffStarLightPath,
} from '@/lib/assetPaths'
import type { StartBuff, StartBuffI18n, BattleKeywords, EnhancementLevel } from '@/types/StartBuffTypes'
import { getEnhancementSuffix, createBuffId, getEnhancementFromBuffId } from '@/types/StartBuffTypes'
import { AutoSizeText } from './AutoSizeText'
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
}

/**
 * Individual start buff card component (edit-only)
 *
 * Enhancement behavior:
 * - Local state for preview, independent of selection
 * - Enhancement button only changes preview
 * - If already selected, also updates selection
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
}: StartBuffCardProps) {
  const [isHovered, setIsHovered] = useState(false)

  // Local enhancement state for preview
  // Initialize from buff.id (which reflects selection if selected)
  const [localEnhancement, setLocalEnhancement] = useState<EnhancementLevel>(
    () => getEnhancementFromBuffId(Number(buff.id))
  )

  // Use local preview state for enhancement
  const enhancement = localEnhancement

  // Show highlight on selection or hover
  const showHighlight = isSelected || isHovered

  // Get the buff data for current enhancement level
  const currentBuffId = createBuffId(buff.baseId, enhancement)
  const displayBuff = allBuffs.find(b => Number(b.id) === currentBuffId) ?? buff

  // Enhancement button click: update preview, and if selected, update selection
  const handleEnhancementClick = (level: 1 | 2) => {
    const newEnhancement: EnhancementLevel = enhancement === level ? 0 : level
    setLocalEnhancement(newEnhancement)

    // Only update selection if already selected
    if (isSelected) {
      const newBuffId = createBuffId(buff.baseId, newEnhancement)
      onSelect(newBuffId)
    }
  }

  // Card click: toggle selection with current enhancement
  const handleCardClick = () => {
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
      className="relative cursor-pointer w-68"
      onMouseEnter={() => { setIsHovered(true) }}
      onMouseLeave={() => { setIsHovered(false) }}
      onClick={handleCardClick}
    >
      {/* Pane background */}
      <img
        src={getStartBuffPanePath()}
        alt=""
        className="w-full h-auto"
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
              className="font-bold text-sm"
              style={{ color: enhancement > 0 ? '#f8c200' : 'white' }}
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
          <div className="ml-2">
            <AutoSizeText
              text={`${displayBuff.name}${getEnhancementSuffix(enhancement)}`}
              width={160}
              className="text-white font-medium"
            />
          </div>
        </div>

        {/* Description - center area */}
        <div className="flex-1 overflow-y-auto px-3 py-2 m-3.5 scrollbar-hide">
          <div className="text-white/90 space-y-0.5">
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
      {showHighlight && (
        <img
          src={getStartBuffHighlightPath()}
          alt=""
          className="absolute inset-0 w-66 h-78 justify-center translate-x-1 translate-y-1.75 pointer-events-none"
        />
      )}
    </div>
  )
}
