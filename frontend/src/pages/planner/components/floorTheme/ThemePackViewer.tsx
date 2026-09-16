import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { CARD_MOBILE_SCALE } from '@/lib/constants'
import { CardSlot, THEME_PACK_GEOMETRY } from '@/shared/cardLayout'
import { EmptyStatePlaceholder } from '@/components/feedback/EmptyStatePlaceholder'
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
      <EmptyStatePlaceholder
        label={
          readOnly
            ? t('pages.plannerMD.emptyState.noThemePack')
            : t('pages.plannerMD.selectThemePack')
        }
        onClick={onClick}
        readOnly={readOnly}
        className="size-full"
      />
    </CardSlot>
  )
}
