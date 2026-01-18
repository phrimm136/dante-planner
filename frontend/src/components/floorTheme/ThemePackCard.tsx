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
  const normalStyle = { left: '3.22%', top: '0.8%', width: '94.06%', height: '97.2%' }
  const extremeStyle = { left: '-2.22%', top: '-1.3%', width: '104.06%', height: '101.37%' }

  return (
    <div
      className={cn(
        'group relative w-60 h-104',
        className
      )}
    >
      {/* Layer 1: Theme pack image - static to define container size */}
      <img
        src={getThemePackImagePath(packId)}
        alt={packName}
        className="w-full h-auto"
      />

      {/* Layer 2: Hover highlight overlay - fills container */}
      {enableHoverHighlight && (
        <img
          src={isExtreme ? getThemePackExtremeHighlightPath() : getThemePackHoverHighlightPath()}
          alt=""
          className="absolute max-w-none object-fill pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity"
          style={isExtreme ? extremeStyle : normalStyle}
        />
      )}

      {/* Layer 3: Theme pack name */}
      <div
        className={`absolute left-0 right-0 flex justify-center items-center pointer-events-none ${!isExtreme ? 'translate-x-[3px]' : '-translate-x-[3px]'} leading-4`}
        style={{ top: !isExtreme ? '72%' : '79%', height: !isExtreme ? 60 : 72 }}
      >
        <AutoSizeText
          text={specialName ? stripColorTags(specialName) : packName}
          width={!isExtreme ? 180 : 170}
          className="text-center"
          style={{ ...displayStyle, ...(!specialName && { color: `#${packEntry.themePackConfig.textColor}` }), filter: 'drop-shadow(1.2px 1.2px 0 rgba(0,0,0,0.9))' }}
          minFontSize={!isExtreme ? 10 : 14}
          maxFontSize={!isExtreme ? 25 : 28}
          lineHeight={lineHeight}
          coloredContent={specialName ? parseColorTags(specialName) : undefined}
        />
      </div>

      {/* Layer 4: Custom overlay */}
      {overlay}
    </div>
  )
}
