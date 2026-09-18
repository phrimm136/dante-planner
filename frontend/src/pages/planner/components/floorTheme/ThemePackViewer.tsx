import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { CARD_MOBILE_SCALE } from '@/lib/constants'
import { CardSlot, THEME_PACK_GEOMETRY } from '@/shared/cardLayout'
import { EmptyStatePlaceholder } from '@/components/feedback/EmptyStatePlaceholder'
import { ThemePackCard } from '@/pages/themePack'
import type { ThemePackSpec } from '@/pages/themePack'

interface ThemePackViewerProps {
  packId: string
  packEntry: ThemePackSpec
  packName: string
  onClick?: () => void
  readOnly?: boolean
  enableHoverHighlight?: boolean
  isSelected?: boolean
  overlay?: ReactNode
  mobileScale?: number
  className?: string
}

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
