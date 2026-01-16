import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import {
  getThemePackImagePath,
  getThemePackHoverHighlightPath,
  getThemePackExtremeHighlightPath,
} from '@/lib/assetPaths'
import { cn, getDisplayFontForLanguage, getLineHeightForLanguage } from '@/lib/utils'
import { isExtremePack } from '@/types/ThemePackTypes'
import type { ThemePackEntry } from '@/types/ThemePackTypes'
import { AutoSizeText } from '@/components/common/AutoSizeText'
import { parseColorTags } from '@/components/startBuff/formatBuffDescription'

/** Strip color tags from text for measurement */
function stripColorTags(text: string): string {
  return text.replace(/<color=#[0-9a-fA-F]{6}>([^<]*)<\/color>/g, '$1')
}

interface ThemePackCardProps {
  packId: string
  packEntry: ThemePackEntry
  packName: string
  /** Special name with embedded color codes (e.g., "<color=#XXXXXX>text</color>") */
  specialName?: string
  /** Enable hover highlight overlay (for selection contexts) */
  enableHoverHighlight?: boolean
  /** Custom overlay content (e.g., selected indicator) */
  overlay?: ReactNode
  className?: string
}

/**
 * Pure view component for rendering a theme pack card.
 * Does NOT include any interaction logic.
 */
export function ThemePackCard({
  packId,
  packEntry,
  packName,
  specialName,
  enableHoverHighlight = false,
  overlay,
  className,
}: ThemePackCardProps) {
  const { i18n } = useTranslation()
  const isExtreme = isExtremePack(packEntry)
  const displayStyle = getDisplayFontForLanguage(i18n.language)
  const lineHeight = getLineHeightForLanguage(i18n.language)

  // Normal frame: 404x716, Extreme frame: 749x1247
  const normalStyle = { left: '3.22%', top: '1.3%', width: '94.06%', height: '97.37%' }
  const extremeStyle = { left: '3.22%', top: '3.3%', width: '94.06%', height: '95.37%' }

  return (
    <div
      className={cn(
        'group relative w-56 overflow-hidden',
        className
      )}
      style={{ aspectRatio: isExtreme ? '749 / 1247' : '404 / 716' }}
    >
      {/* Layer 1: Theme pack image - positioned within frame's glow area */}
      <img
        src={getThemePackImagePath(packId)}
        alt={packName}
        className="absolute object-cover"
        style={isExtreme ? extremeStyle : normalStyle}
      />

      {/* Layer 2: Hover highlight overlay - fills container */}
      {enableHoverHighlight && (
        <img
          src={isExtreme ? getThemePackExtremeHighlightPath() : getThemePackHoverHighlightPath()}
          alt=""
          className="absolute inset-0 w-full h-full object-fill pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity"
        />
      )}

      {/* Layer 3: Theme pack name */}
      <div className="absolute bottom-[16%] left-0 right-0 flex justify-center pointer-events-none">
        <AutoSizeText
          text={specialName ? stripColorTags(specialName) : packName}
          width={200}
          className="text-center"
          style={{ ...displayStyle, ...(!specialName && { color: `#${packEntry.themePackConfig.textColor}` }) }}
          minFontSize={10}
          maxFontSize={20}
          lineHeight={lineHeight}
          coloredContent={specialName ? parseColorTags(specialName) : undefined}
        />
      </div>

      {/* Layer 4: Custom overlay */}
      {overlay}
    </div>
  )
}
