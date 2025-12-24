import { useState } from 'react'
import {
  getStartBuffIconPath,
  getStartBuffPanePath,
  getStartBuffHighlightPath,
  getStartBuffStarLightPath,
} from '@/lib/assetPaths'
import type { StartBuff, EnhancementLevel, StartBuffI18n, BattleKeywords } from '@/types/StartBuffTypes'
import { getEnhancementSuffix, createBuffId } from '@/types/StartBuffTypes'
import { AutoSizeText } from './AutoSizeText'
import { EnhancementButton } from './EnhancementButton'
import { formatBuffEffects } from './formatBuffDescription'

interface StartBuffCardProps {
  buff: StartBuff
  allBuffs: StartBuff[]
  i18n: StartBuffI18n
  battleKeywords?: BattleKeywords
  isSelected: boolean
  onSelect: (buffId: number) => void
}

/**
 * Individual start buff card component
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
  // 강화 상태는 로컬로 관리 (버프 선택과 독립적)
  const [enhancement, setEnhancement] = useState<EnhancementLevel>(0)

  const showHighlight = isHovered || isSelected

  // Get the buff data for current enhancement level
  const currentBuffId = createBuffId(buff.baseId, enhancement)
  const displayBuff = allBuffs.find(b => Number(b.id) === currentBuffId) || buff

  // 강화 버튼 클릭: 강화 상태만 토글 (선택 상태 변경 X)
  const handleEnhancementClick = (level: 1 | 2) => {
    const newEnhancement: EnhancementLevel = enhancement === level ? 0 : level
    setEnhancement(newEnhancement)

    // 이미 선택된 상태라면 새 강화 레벨로 선택 업데이트
    if (isSelected) {
      const newBuffId = createBuffId(buff.baseId, newEnhancement)
      onSelect(newBuffId)
    }
  }

  // 카드 클릭: 현재 강화 상태로 선택/해제
  const handleCardClick = () => {
    if (isSelected) {
      // Deselect - signal with negative ID
      onSelect(-currentBuffId)
    } else {
      onSelect(currentBuffId)
    }
  }

  return (
    <div
      className="relative cursor-pointer"
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
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
            onClick={() => handleEnhancementClick(1)}
          />
          <EnhancementButton
            level={2}
            isSelected={enhancement === 2}
            onClick={() => handleEnhancementClick(2)}
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
