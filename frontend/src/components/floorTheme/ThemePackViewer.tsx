import { useTranslation } from 'react-i18next'
import { getThemePackImagePath } from '@/lib/assetPaths'
import { cn } from '@/lib/utils'
import type { ThemePackEntry } from '@/types/ThemePackTypes'
import { isExtremePack } from '@/types/ThemePackTypes'

interface ThemePackViewerProps {
  packId: string
  packEntry: ThemePackEntry
  packName: string
  onClick?: () => void
  className?: string
}

/**
 * Displays a selected theme pack with pre-composed image and name overlay
 */
export function ThemePackViewer({
  packId,
  packEntry,
  packName,
  onClick,
  className,
}: ThemePackViewerProps) {
  const isExtreme = isExtremePack(packEntry)
  const textColor = `#${packEntry.themePackConfig.textColor}`

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={packName}
      className={cn(
        'relative w-56 h-104 overflow-hidden cursor-pointer',
        'transition-transform',
        className
      )}
    >
      {/* Pre-composed theme pack image */}
      <img
        src={getThemePackImagePath(packId)}
        alt={packName}
        className="w-full h-full object-cover"
      />

      {/* Theme pack name overlay */}
      <div
        className={cn(
          'absolute left-0 right-0 px-2 py-1 text-center',
          // Different position for extreme vs normal packs
          isExtreme ? 'bottom-16' : 'bottom-8'
        )}
      >
        <span
          className="text-sm font-bold drop-shadow-[0_2px_2px_rgba(0,0,0,0.8)]"
          style={{ color: textColor }}
        >
          {packName}
        </span>
      </div>
      {/*  */}
    </button>
  )
}

interface ThemePackPlaceholderProps {
  onClick?: () => void
  className?: string
}

/**
 * Placeholder shown when no theme pack is selected
 */
export function ThemePackPlaceholder({
  onClick,
  className,
}: ThemePackPlaceholderProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={t('pages.plannerMD.selectThemePack')}
      className={cn(
        'selectable relative w-56 h-104 rounded-lg border-2 border-dashed border-muted-foreground/50',
        'flex items-center justify-center cursor-pointer',
        className
      )}
    >
      <span className="text-sm text-muted-foreground text-center px-4">
        {t('pages.plannerMD.selectThemePack')}
      </span>
    </button>
  )
}
