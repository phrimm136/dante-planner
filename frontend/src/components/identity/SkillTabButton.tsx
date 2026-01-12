import { useTranslation } from 'react-i18next'

import { getAttributeColors } from '@/lib/colorUtils'
import { cn, getDisplayFontForLanguage } from '@/lib/utils'

interface SkillTabButtonProps {
  /** Skill attribute type for color theming */
  attributeType?: string
  /** Button label text */
  label: string
  /** Click handler */
  onClick: () => void
  /** Whether this skill is currently selected */
  isActive: boolean
  /** Whether this skill is locked (e.g., Skill 3 before Uptie 3) */
  isLocked?: boolean
}

/**
 * Game-authentic yellow text color for selected/hovered states
 * Matches the game's highlight color for interactive elements
 */
const YELLOW_HIGHLIGHT = '#ffd700'

/**
 * Darkens a hex color for hover/select states
 * @param hex - Hex color string (e.g., "#40A1B5")
 * @param amount - Darkening factor (0-1, where 0.15 = 15% darker)
 * @returns Darkened hex color string
 */
function darkenColor(hex: string, amount: number): string {
  const cleanHex = hex.replace('#', '')
  const r = parseInt(cleanHex.substring(0, 2), 16)
  const g = parseInt(cleanHex.substring(2, 4), 16)
  const b = parseInt(cleanHex.substring(4, 6), 16)

  // Darken by moving toward black (0)
  const factor = 1 - amount
  const newR = Math.round(r * factor)
  const newG = Math.round(g * factor)
  const newB = Math.round(b * factor)

  const toHex = (n: number) => n.toString(16).padStart(2, '0')
  return `#${toHex(newR)}${toHex(newG)}${toHex(newB)}`
}

/**
 * SkillTabButton - Game-authentic skill selector button
 *
 * Styling states match Limbus Company game aesthetics:
 * - **Default**: Standard bg-muted appearance
 * - **Hover OR Selected**: Darkened attribute color background, yellow text
 * - **Selected + Hover**: Original attribute color background (brighter), yellow text
 * - **Locked**: Reduced opacity with lock icon
 *
 * Pattern: Uses inline styles for dynamic attribute colors, Tailwind for base layout
 */
export function SkillTabButton({
  attributeType,
  label,
  onClick,
  isActive,
  isLocked = false,
}: SkillTabButtonProps) {
  const { i18n } = useTranslation()
  const { primary } = getAttributeColors(attributeType)

  // Darkened version for hover/select states (15% darker)
  const darkenedPrimary = darkenColor(primary, 0.15)

  // Base classes - default state uses bg-muted
  const baseClasses = cn(
    'flex-1 py-2 px-4 rounded font-medium transition-all duration-200',
    !isActive && 'bg-muted',
    isLocked && 'opacity-50 cursor-not-allowed'
  )

  return (
    <button
      onClick={onClick}
      disabled={isLocked}
      className={baseClasses}
      style={
        isActive
          ? {
              backgroundColor: darkenedPrimary,
              color: YELLOW_HIGHLIGHT,
              textShadow: '1px 1px 2px rgba(0, 0, 0, 0.8)',
            }
          : undefined
      }
      onMouseEnter={(e) => {
        if (!isLocked) {
          if (isActive) {
            // Selected + Hover: original primary color (brighter)
            e.currentTarget.style.backgroundColor = primary
          } else {
            // Hover only: darkened attribute color
            e.currentTarget.style.backgroundColor = darkenedPrimary
            e.currentTarget.style.color = YELLOW_HIGHLIGHT
            e.currentTarget.style.textShadow = '1px 1px 2px rgba(0, 0, 0, 0.8)'
          }
        }
      }}
      onMouseLeave={(e) => {
        if (!isLocked) {
          if (isActive) {
            // Return to selected state (darkened)
            e.currentTarget.style.backgroundColor = darkenedPrimary
          } else {
            // Return to default state
            e.currentTarget.style.backgroundColor = ''
            e.currentTarget.style.color = ''
            e.currentTarget.style.textShadow = ''
          }
        }
      }}
    >
      <span style={getDisplayFontForLanguage(i18n.language)}>{label}</span>
      {isLocked && <span className="ml-1 text-xs">🔒</span>}
    </button>
  )
}
