import { getStartBuffIconPath, getStartBuffMiniPath, getStartBuffMiniHighlightPath } from '@/lib/assetPaths'
import { MD_ACCENT_COLORS } from '@/lib/constants'
import { getEnhancementFromBuffId, getBaseIdFromBuffId, getEnhancementSuffix } from '@/types/StartBuffTypes'
import { EGOGiftEnhancementIndicator } from '@/components/egoGift/EGOGiftEnhancementIndicator'
import { useTranslation } from 'react-i18next'
import { getDisplayFontForLanguage, getLineHeightForLanguage } from '@/lib/utils'
import { AutoSizeWrappedText } from '@/components/common/AutoSizeWrappedText'

interface StartBuffMiniCardProps {
  /** Full buff ID including enhancement (e.g., 101, 202, 303) */
  buffId: number
  /** Localized display name (e.g., "Starlight of Eden") */
  displayName: string
  /** Mirror Dungeon version (from usePlannerConfig) */
  mdVersion: number
}

/**
 * Compact 96x96px card for displaying selected start buffs in summary view.
 * Shows buff icon (upper half), name with enhancement suffix (lower half),
 * enhancement indicator (top-right), and hover highlight overlay.
 *
 * @example
 * const config = usePlannerConfig()
 * <StartBuffMiniCard buffId={202} displayName="Starlight of Eden" mdVersion={config.mdCurrentVersion} />
 * // Renders: icon + "Starlight of Eden++" with +2 indicator
 */
export function StartBuffMiniCard({ buffId, displayName, mdVersion }: StartBuffMiniCardProps) {
  const baseId = getBaseIdFromBuffId(buffId)
  const enhancement = getEnhancementFromBuffId(buffId)
  const suffix = getEnhancementSuffix(enhancement)
  const accentColor = MD_ACCENT_COLORS[mdVersion]
  const { i18n } = useTranslation()
  const displayStyle = getDisplayFontForLanguage(i18n.language)
  const lineHeight = getLineHeightForLanguage(i18n.language)

  return (
    <div className="group relative w-24 h-24">
      {/* Background image */}
      <img
        src={getStartBuffMiniPath(mdVersion)}
        alt=""
        className="absolute inset-0 w-full h-full object-contain"
      />

      {/* Content container - flex column for vertical layout */}
      <div className="absolute inset-0 flex flex-col gap-2">
        {/* Upper half: Buff icon (centered) */}
        <div className="flex-1 flex items-center justify-center pt-1">
          <img
            src={getStartBuffIconPath(baseId, mdVersion)}
            alt=""
            className="w-12 h-12 object-contain"
          />
        </div>

        {/* Lower half: Name + enhancement suffix */}
        <div className="flex-1 flex items-center justify-center px-1">
          <AutoSizeWrappedText
            text={`${displayName}${suffix}`}
            width={80}
            maxLines={2}
            className="text-center leading-tight overflow-hidden text-ellipsis whitespace-nowrap max-w-full"
            style={{ color: accentColor, ...displayStyle }}
            minFontSize={12}
            maxFontSize={12}
            lineHeight={lineHeight}
            wordBreak="keep-all"
          />
        </div>
      </div>

      {/* Enhancement indicator - top-right */}
      <div className="scale-50 translate-x-4 translate-y-1">
        <EGOGiftEnhancementIndicator enhancement={enhancement} />
      </div>

      {/* Hover overlay */}
      <img
        src={getStartBuffMiniHighlightPath(mdVersion)}
        alt=""
        className="absolute inset-0 w-full h-full object-contain pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity"
      />
    </div>
  )
}
