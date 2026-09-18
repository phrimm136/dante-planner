import { useTranslation } from 'react-i18next'

import { getLockIconPath } from '@/shared/assets'
import { getAttributeColors } from '@/shared/gameData'
import { withAlpha } from '@/lib/colorUtils'
import { cn, getDisplayFontForLanguage } from '@/lib/utils'

interface SkillTabButtonProps {
  attributeType?: string | undefined
  label: string
  onClick: () => void
  isActive: boolean
  isLocked?: boolean
}

/**
 * Game-authentic yellow text color for selected/hovered states
 * Matches the game's highlight color for interactive elements
 */
const YELLOW_HIGHLIGHT = '#ffd700'

const OVERLAY_ALPHA = 0.5

const STACKED_OVERLAY_ALPHA = 1 - (1 - OVERLAY_ALPHA) ** 2

export function SkillTabButton({
  attributeType,
  label,
  onClick,
  isActive,
  isLocked = false,
}: SkillTabButtonProps) {
  const { i18n } = useTranslation(['database', 'common'])
  const { primary } = getAttributeColors(attributeType)
  const overlay = withAlpha(primary, OVERLAY_ALPHA)
  const stackedOverlay = withAlpha(primary, STACKED_OVERLAY_ALPHA)

  const baseClasses = cn(
    'flex-1 py-2 px-4 rounded font-medium transition-all duration-200 bg-muted',
  )

  const getButtonStyle = (): React.CSSProperties | undefined => {
    if (isActive) {
      return {
        backgroundColor: overlay,
        color: isLocked ? undefined : YELLOW_HIGHLIGHT,
        textShadow: isLocked ? undefined : '1px 1px 2px rgba(0, 0, 0, 0.8)',
      }
    }
    return undefined
  }

  return (
    <button
      onClick={onClick}
      className={baseClasses}
      style={getButtonStyle()}
      onMouseEnter={(e) => {
        if (isActive) {
          e.currentTarget.style.backgroundColor = stackedOverlay
        } else {
          e.currentTarget.style.backgroundColor = overlay
          if (!isLocked) {
            e.currentTarget.style.color = YELLOW_HIGHLIGHT
            e.currentTarget.style.textShadow = '1px 1px 2px rgba(0, 0, 0, 0.8)'
          }
        }
      }}
      onMouseLeave={(e) => {
        if (isActive) {
          e.currentTarget.style.backgroundColor = overlay
        } else {
          e.currentTarget.style.backgroundColor = ''
          e.currentTarget.style.color = ''
          e.currentTarget.style.textShadow = ''
        }
      }}
    >
      {isLocked && <img src={getLockIconPath()} alt="" className="mr-1 inline-block h-6" />}
      <span
        className={cn(isLocked && 'text-muted-foreground')}
        style={getDisplayFontForLanguage(i18n.language)}
      >
        {label}
      </span>
    </button>
  )
}
