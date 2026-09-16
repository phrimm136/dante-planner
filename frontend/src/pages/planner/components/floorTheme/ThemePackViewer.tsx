import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { CARD_MOBILE_SCALE } from '@/lib/constants'
import { THEME_PACK_GEOMETRY } from '@/pages/themePack'
import { CardSlot } from '@/shared/cardLayout'
import { cn } from '@/lib/utils'
import { ThemePackCard } from '@/pages/themePack'
import type { ThemePackEntry } from '@/pages/themePack'

/** The theme pack box with its height left to the card. */
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
  /** The share of the desktop width the card takes below the desktop breakpoint */
  mobileScale?: number
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
  mobileScale = CARD_MOBILE_SCALE,
  className,
}: ThemePackViewerProps) {
  return (
    <CardSlot size={THEME_PACK_GEOMETRY.size} mobileScale={mobileScale} className={className}>
      {readOnly ? (
        <div aria-label={packName}>
          <ThemePackCard
            packId={packId}
            packEntry={packEntry}
            enableHoverHighlight={enableHoverHighlight}
            isSelected={isSelected}
            overlay={overlay}
          />
        </div>
      ) : (
        <button type="button" onClick={onClick} aria-label={packName} className="block w-full">
          <ThemePackCard
            packId={packId}
            packEntry={packEntry}
            enableHoverHighlight={enableHoverHighlight}
            overlay={overlay}
          />
        </button>
      )}
    </CardSlot>
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
    <CardSlot size={THEME_PACK_GEOMETRY.size} className={className}>
      <button
        type="button"
        onClick={readOnly ? undefined : onClick}
        disabled={readOnly}
        aria-label={t('pages.plannerMD.selectThemePack')}
        className={cn(
          'size-full border-2 border-dashed border-muted-foreground/50',
          'flex items-center justify-center',
          !readOnly ? 'selectable' : 'rounded-md',
        )}
      >
        <span className="text-sm text-muted-foreground text-center px-4">
          {readOnly
            ? t('pages.plannerMD.emptyState.noThemePack')
            : t('pages.plannerMD.selectThemePack')}
        </span>
      </button>
    </CardSlot>
  )
}
