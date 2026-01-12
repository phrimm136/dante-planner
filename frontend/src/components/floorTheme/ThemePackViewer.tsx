import { useTranslation } from 'react-i18next'
import { getThemePackImagePath } from '@/lib/assetPaths'
import { cn } from '@/lib/utils'
import type { ThemePackEntry } from '@/types/ThemePackTypes'

interface ThemePackViewerProps {
  packId: string
  packEntry: ThemePackEntry
  packName: string
  onClick?: () => void
  readOnly?: boolean
  className?: string
}

/**
 * Displays a selected theme pack with pre-composed image and name overlay
 */
export function ThemePackViewer({
  packId,
  packName,
  onClick,
  readOnly = false,
  className,
}: ThemePackViewerProps) {
  return (
    <button
      type="button"
      onClick={readOnly ? undefined : onClick}
      disabled={readOnly}
      aria-label={packName}
      className={cn(
        'relative w-56 h-104 overflow-hidden flex items-center justify-center',
        !readOnly && 'selectable',
        'transition-transform',
        className
      )}
    >
      {/* Pre-composed theme pack image - centered */}
      <img
        src={getThemePackImagePath(packId)}
        alt={packName}
        className="w-full h-full object-cover object-center"
      />
    </button>
  )
}

interface ThemePackPlaceholderProps {
  onClick?: () => void
  readOnly?: boolean
  className?: string
}

/**
 * Placeholder shown when no theme pack is selected
 */
export function ThemePackPlaceholder({
  onClick,
  readOnly = false,
  className,
}: ThemePackPlaceholderProps) {
  const { t } = useTranslation(['planner', 'common'])

  return (
    <button
      type="button"
      onClick={readOnly ? undefined : onClick}
      disabled={readOnly}
      aria-label={t('pages.plannerMD.selectThemePack')}
      className={cn(
        'relative w-56 h-104 rounded-lg border-2 border-dashed border-muted-foreground/50',
        'flex items-center justify-center',
        !readOnly && 'selectable',
        className
      )}
    >
      <span className="text-sm text-muted-foreground text-center px-4">
        {readOnly
          ? t('pages.plannerMD.emptyState.noThemePack')
          : t('pages.plannerMD.selectThemePack')}
      </span>
    </button>
  )
}
