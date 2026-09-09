import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/utils'
import { ThemePackCard } from '@/pages/themePack'
import type { ThemePackEntry } from '@/pages/themePack'

interface ThemePackViewerProps {
  packId: string
  packEntry: ThemePackEntry
  /** Accessible label; the card prints the localized name itself */
  packName: string
  onClick?: () => void
  readOnly?: boolean
  enableHoverHighlight?: boolean
  isSelected?: boolean
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
  onClick,
  readOnly = false,
  enableHoverHighlight = false,
  isSelected = false,
  overlay,
  className,
}: ThemePackViewerProps) {
  if (readOnly) {
    return (
      <div aria-label={packName} className={className}>
        <ThemePackCard
          packId={packId}
          packEntry={packEntry}
          enableHoverHighlight={enableHoverHighlight}
          isSelected={isSelected}
          overlay={overlay}
        />
      </div>
    )
  }

  return (
    <button type="button" onClick={onClick} aria-label={packName} className={className}>
      <ThemePackCard
        packId={packId}
        packEntry={packEntry}
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
        'relative top-2 w-56 h-100 border-2 border-dashed border-muted-foreground/50',
        'flex items-center justify-center',
        !readOnly ? 'selectable' : 'rounded-md',
        className,
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
