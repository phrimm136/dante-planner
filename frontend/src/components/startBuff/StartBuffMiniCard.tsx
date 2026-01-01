import { getStartBuffIconPath, getStartBuffMiniPath, getStartBuffMiniHighlightPath } from '@/lib/assetPaths'
import { CURRENT_MD_VERSION, MD_ACCENT_COLORS } from '@/lib/constants'
import { getEnhancementFromBuffId, getBaseIdFromBuffId, getEnhancementSuffix } from '@/types/StartBuffTypes'
import { EGOGiftEnhancementIndicator } from '@/components/egoGift/EGOGiftEnhancementIndicator'

interface StartBuffMiniCardProps {
  /** Full buff ID including enhancement (e.g., 101, 202, 303) */
  buffId: number
  /** Localized display name (e.g., "Starlight of Eden") */
  displayName: string
}

/**
 * Compact 96x96px card for displaying selected start buffs in summary view.
 * Shows buff icon (upper half), name with enhancement suffix (lower half),
 * enhancement indicator (top-right), and hover highlight overlay.
 *
 * @example
 * <StartBuffMiniCard buffId={202} displayName="Starlight of Eden" />
 * // Renders: icon + "Starlight of Eden++" with +2 indicator
 */
export function StartBuffMiniCard({ buffId, displayName }: StartBuffMiniCardProps) {
  const baseId = getBaseIdFromBuffId(buffId)
  const enhancement = getEnhancementFromBuffId(buffId)
  const suffix = getEnhancementSuffix(enhancement)
  const accentColor = MD_ACCENT_COLORS[CURRENT_MD_VERSION]

  return (
    <div className="group relative w-24 h-24">
      {/* Background image */}
      <img
        src={getStartBuffMiniPath()}
        alt=""
        className="absolute inset-0 w-full h-full object-contain"
      />

      {/* Content container - flex column for vertical layout */}
      <div className="absolute inset-0 flex flex-col">
        {/* Upper half: Buff icon (centered) */}
        <div className="flex-1 flex items-center justify-center pt-1">
          <img
            src={getStartBuffIconPath(baseId)}
            alt=""
            className="w-10 h-10 object-contain"
          />
        </div>

        {/* Lower half: Name + enhancement suffix */}
        <div className="flex-1 flex items-start justify-center px-1">
          <span
            className="text-[10px] font-medium text-center leading-tight overflow-hidden text-ellipsis whitespace-nowrap max-w-full"
            style={{ color: accentColor }}
          >
            {displayName}{suffix}
          </span>
        </div>
      </div>

      {/* Enhancement indicator - top-right */}
      <EGOGiftEnhancementIndicator enhancement={enhancement} />

      {/* Hover overlay */}
      <img
        src={getStartBuffMiniHighlightPath()}
        alt=""
        className="absolute inset-0 w-full h-full object-contain pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity"
      />
    </div>
  )
}
