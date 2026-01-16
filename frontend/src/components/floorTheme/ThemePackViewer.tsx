import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'
import { ThemePackCard } from './ThemePackCard'
import type { ThemePackEntry } from '@/types/ThemePackTypes'

interface ThemePackViewerProps {
  packId: string
  packEntry: ThemePackEntry
  packName: string
  /** Special name with embedded color codes */
  specialName?: string
  onClick?: () => void
  readOnly?: boolean
  enableHoverHighlight?: boolean
  overlay?: ReactNode
  className?: string
}

/**
 * Interactive wrapper for ThemePackCard.
 * Use this when clicking the card should trigger an action.
 */
export function ThemePackViewer({
  packId,
  packEntry,
  packName,
  specialName,
  onClick,
  readOnly = false,
  enableHoverHighlight = false,
  overlay,
  className,
}: ThemePackViewerProps) {
  return (
    <button
      type="button"
      onClick={readOnly ? undefined : onClick}
      disabled={readOnly}
      aria-label={packName}
      className={className}
    >
      <ThemePackCard
        packId={packId}
        packEntry={packEntry}
        packName={packName}
        specialName={specialName}
        enableHoverHighlight={enableHoverHighlight}
        overlay={overlay}
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
        'relative w-56 h-104 border-2 border-dashed border-muted-foreground/50',
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
