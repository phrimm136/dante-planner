import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { EGOGiftCard, toEGOGiftCardProps } from '@/pages/egoGift'
import type { EGOGiftListItem } from '@/pages/egoGift'
import { getKeywordIconPath } from '@/shared/assets'
import { useEGOGiftListData } from '@/pages/egoGift'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import { ScaledCardWrapper } from '@/components/layout/ScaledCardWrapper'
import { CARD_GRID, SECTION_STYLES } from '@/lib/constants'
import { cn } from '@/lib/utils'

export interface StartGiftSummaryProps {
  selectedKeyword: string | null
  selectedGiftIds: Set<string>
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
  const { spec, i18n } = useEGOGiftListData()

  const mobileScale = CARD_GRID.MOBILE_SCALE.STANDARD

  // Show selected state when keyword is chosen (gifts are optional)
  const hasKeywordSelected = selectedKeyword !== null

  // Build gift objects for display
  const selectedGifts = (() => {
    if (!hasKeywordSelected || !spec || !i18n || selectedGiftIds.size === 0) return []

    return Array.from(selectedGiftIds).map((giftId) => {
      const giftSpec = spec[giftId]
      const name = i18n[giftId] || `Gift ${giftId}`

      if (!giftSpec) {
        return {
          id: giftId,
          name,
          tag: ['TIER_1'],
          keyword: null,
          battleKeywordList: [],
          attributeType: 'CRIMSON',
          themePack: [],
          maxEnhancement: 0,
        } satisfies EGOGiftListItem
      }

      return { ...toEGOGiftCardProps(giftId, giftSpec), name }
    })
  })()

  return (
    <PlannerSection title={t('pages.plannerMD.startEgoGift')} onViewNotes={onViewNotes}>
      <button
        type="button"
        onClick={onClick}
        className={cn('w-full text-left', !readOnly && 'selectable cursor-pointer')}
      >
        {hasKeywordSelected ? (
          /* Selected state: keyword icon + gift cards (if any) + EA counter */
          <div className="flex items-center gap-4 p-2 min-h-28">
            {/* Keyword icon */}
            <ScaledCardWrapper
              mobileScale={mobileScale}
              cardWidth={CARD_GRID.WIDTH.KEYWORD_ICON}
              cardHeight={CARD_GRID.HEIGHT.KEYWORD_ICON}
              className="shrink-0"
            >
              <div className="w-16 h-16 flex items-center justify-center">
                <img
                  src={getKeywordIconPath(selectedKeyword)}
                  alt={selectedKeyword}
                  className="w-12 h-12 object-contain"
                />
              </div>
            </ScaledCardWrapper>

            {/* Selected gift cards (if any) */}
            <div className={SECTION_STYLES.LAYOUT.wrap}>
              {selectedGifts.length > 0 ? (
                selectedGifts.map((gift) => (
                  <ScaledCardWrapper
                    key={gift.id}
                    mobileScale={mobileScale}
                    cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
                    cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
                  >
                    <EGOGiftCard gift={gift} />
                  </ScaledCardWrapper>
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
            <span className={SECTION_STYLES.TEXT.muted}>
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
