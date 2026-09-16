import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/layout/PlannerSection'
import {
  EGOGiftCard,
  getBaseGiftId,
  toEGOGiftCardProps,
  toUnknownGiftListItem,
} from '@/pages/egoGift'
import type { EncodedGiftId } from '@/shared/gameData'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/pages/egoGift'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import { CardSlot } from '@/shared/cardLayout'
import { CARD_MOBILE_SCALE, SECTION_STYLES } from '@/lib/constants'
import { KEYWORD_ICON_GEOMETRY } from '../../lib/cardLayout'
import { EGO_GIFT_GEOMETRY } from '@/pages/egoGift'
import { StartGiftKeywordIcon } from './StartGiftKeywordIcon'
import { cn } from '@/lib/utils'

export interface StartGiftSummaryProps {
  selectedKeyword: string | null
  selectedGiftIds: ReadonlySet<EncodedGiftId>
  onClick?: () => void
  readOnly?: boolean
  onViewNotes?: () => void
}

/**
 * Summary view for start gift selection.
 * Shows selected keyword + gift cards when selection exists,
 * or a dashed border placeholder when empty.
 * Clicking opens the StartGiftEditPane dialog.
 */
export function StartGiftSummary({
  selectedKeyword,
  selectedGiftIds,
  onClick,
  readOnly = false,
  onViewNotes,
}: StartGiftSummaryProps) {
  const { t } = useTranslation(['planner', 'common'])
  const spec = useEGOGiftListSpec()
  const i18n = useEGOGiftListI18n()

  const mobileScale = CARD_MOBILE_SCALE

  // Show selected state when keyword is chosen (gifts are optional)
  const hasKeywordSelected = selectedKeyword !== null

  // Build gift objects for display
  const selectedGifts = (() => {
    if (!hasKeywordSelected || !spec || !i18n || selectedGiftIds.size === 0) return []

    return Array.from(selectedGiftIds).map((encodedId) => {
      const giftId = getBaseGiftId(encodedId)
      const giftSpec = spec[giftId]
      const name = i18n[giftId] || `Gift ${giftId}`

      if (!giftSpec) return toUnknownGiftListItem(giftId, name)

      return { ...toEGOGiftCardProps(giftId, giftSpec), name }
    })
  })()

  return (
    <PlannerSection
      title={t('pages.plannerMD.startEgoGift')}
      {...(onViewNotes !== undefined && { onViewNotes })}
    >
      <button
        type="button"
        onClick={onClick}
        className={cn('w-full text-left', !readOnly && 'selectable cursor-pointer')}
      >
        {hasKeywordSelected ? (
          /* Selected state: keyword icon + gift cards (if any) + EA counter */
          <div className="flex items-center gap-4 p-2 min-h-28">
            {/* Keyword icon */}
            <CardSlot
              size={KEYWORD_ICON_GEOMETRY.size}
              mobileScale={mobileScale}
              className="shrink-0"
            >
              <StartGiftKeywordIcon keyword={selectedKeyword} />
            </CardSlot>

            {/* Selected gift cards (if any) */}
            <div className={SECTION_STYLES.LAYOUT.wrap}>
              {selectedGifts.length > 0 ? (
                selectedGifts.map((gift) => (
                  <CardSlot key={gift.id} size={EGO_GIFT_GEOMETRY.size} mobileScale={mobileScale}>
                    <EGOGiftCard gift={gift} />
                  </CardSlot>
                ))
              ) : (
                <span className={SECTION_STYLES.TEXT.caption}>
                  {t('pages.plannerMD.noEgoGiftSelected')}
                </span>
              )}
            </div>
          </div>
        ) : (
          /* Empty state: dashed border placeholder - min-h-28 matches selected state */
          <div className="flex items-center justify-center min-h-28 border-2 border-dashed border-muted-foreground/50 rounded-lg">
            <span className={SECTION_STYLES.TEXT.caption}>
              {readOnly
                ? t('pages.plannerMD.emptyState.noStartGifts')
                : t('pages.plannerMD.selectStartEgoGift')}
            </span>
          </div>
        )}
      </button>
    </PlannerSection>
  )
}

/** Props a store-bound caller supplies; the selection comes from the store. */
export type StoreBoundStartGiftSummaryProps = Omit<
  StartGiftSummaryProps,
  'selectedKeyword' | 'selectedGiftIds'
>

/** Renders the summary against the gift selection held by the planner editor store. */
export function StoreBoundStartGiftSummary(props: StoreBoundStartGiftSummaryProps) {
  const selectedKeyword = usePlannerEditorStore((s) => s.selectedGiftKeyword)
  const selectedGiftIds = usePlannerEditorStore((s) => s.selectedGiftIds)

  return (
    <StartGiftSummary
      {...props}
      selectedKeyword={selectedKeyword}
      selectedGiftIds={selectedGiftIds}
    />
  )
}
