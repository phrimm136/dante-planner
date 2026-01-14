import { getStartBuffEnhancementIconPath, getStartBuffEnhancementBgPath } from '@/lib/assetPaths'
import type { EnhancementLevel } from '@/types/StartBuffTypes'

interface EnhancementButtonProps {
  level: 1 | 2
  isSelected: boolean
  onClick: () => void
}

/**
 * Enhancement button component for + or ++ enhancement
 * Wrapper fixes layout position, inner button/image can be styled freely
 */
export function EnhancementButton({ level, isSelected, onClick }: EnhancementButtonProps) {
  const iconPath = isSelected
    ? getStartBuffEnhancementIconPath(level as EnhancementLevel)
    : getStartBuffEnhancementIconPath(0)

  const iconCount = level === 2 && !isSelected ? 2 : 1

  const bgLevel = isSelected ? level : 0
  const bgPath = getStartBuffEnhancementBgPath(bgLevel)

  // 이미지 분석 결과:
  // unselected: 87x51, 테두리만 (glow 없음)
  // plus1: 97x64, 테두리 + glow 6px
  // plus2: 100x65, 테두리 + glow 6px
  // -> glow만 overflow로 바깥에, 버튼 크기는 동일

  const stateKey = isSelected ? (level === 1 ? 'plus1' : 'plus2') : 'unselected'

  // slice: 테두리+glow 영역 (9-slice corner)
  const sliceValues: Record<string, number> = {
    unselected: 20,
    plus1: 28,
    plus2: 32,
  }

  const borderValues: Record<string, number> = {
    unselected: 10,
    plus1: 13,
    plus2: 13,
  }

  // outset: glow가 버튼 바깥으로 나가는 크기
  const outsetValues: Record<string, number> = {
    unselected: 0,
    plus1: 2,
    plus2: 2,
  }

  return (
    // Wrapper: 레이아웃 고정, overflow-visible로 glow 표시
    <div className="flex-1 h-6 relative overflow-visible">
      {/* Background: 9-slice, outset으로 glow 바깥 표시 */}
      <button
        onClick={(e) => {
          e.stopPropagation()
          onClick()
        }}
        className="absolute inset-0 overflow-visible"
        style={{
          borderStyle: 'solid',
          borderWidth: `${borderValues[stateKey]}px`,
          borderImageSource: `url('${bgPath}')`,
          borderImageSlice: `${sliceValues[stateKey]} fill`,
          borderImageOutset: `${outsetValues[stateKey]}px`,
          borderImageRepeat: 'stretch',
        }}
      />
      {/* Icon */}
      <div className="absolute inset-0 flex items-center justify-center gap-0.5 pointer-events-none">
        {Array.from({ length: iconCount }).map((_, i) => (
          <img
            key={i}
            src={iconPath}
            alt=""
            className={`w-auto shrink-0 ${isSelected ? level === 2 ? 'h-[20.8px]' : 'h-[16.9px]' : 'h-4'}`}
          />
        ))}
      </div>
    </div>
  )
}
