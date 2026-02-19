import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOGiftObservationData } from '@/hooks/useEGOGiftObservationData'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { usePlannerEditorStoreSafe } from '@/stores/usePlannerEditorStore'
import { EMPTY_STATE, CARD_GRID } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import { PlannerSection } from '@/components/common/PlannerSection'
import { StarlightCostDisplay } from '@/components/common/StarlightCostDisplay'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
import { EGOGiftCard } from './EGOGiftCard'

interface EGOGiftObservationSummaryProps {
  mdVersion: number
  onClick?: () => void
  readOnly?: boolean
  onViewNotes?: () => void
  /** Override selectedGiftIds from store (for tracker mode) */
  selectedGiftIdsOverride?: Set<string>
}

/**
 * EGO Gift Observation Summary component.
 * Displays selected gifts horizontally with cost. Clicking opens EditPane.
 * Pattern: StartBuffSection (clickable summary with cost right-aligned)
 * Suspends while loading - wrap in Suspense boundary
 */
export function EGOGiftObservationSummary({
  mdVersion,
  onClick,
  readOnly = false,
  onViewNotes,
  selectedGiftIdsOverride,
}: EGOGiftObservationSummaryProps) {
  // Store state (safe - returns undefined if outside context)
  const storeSelectedGiftIds = usePlannerEditorStoreSafe((s) => s.observationGiftIds)
  const selectedGiftIds = selectedGiftIdsOverride ?? storeSelectedGiftIds!
  const { t } = useTranslation(['planner', 'common'])

  // Breakpoint detection for scaling


  const mobileScale = CARD_GRID.MOBILE_SCALE.STANDARD

  // Load observation data for cost calculation (suspends)
  const { data: observationData } = useEGOGiftObservationData(mdVersion)
  const { spec, i18n } = useEGOGiftListData()

  // Calculate current cost based on selection count
  const currentCost =
    observationData.observationEgoGiftCostDataList.find(
      (cost) => cost.egogiftCount === selectedGiftIds.size
    )?.starlightCost || 0

  // Build gift list items for selected gifts
  const selectedGifts = useMemo<EGOGiftListItem[]>(() => {
    const gifts: EGOGiftListItem[] = []
    for (const id of selectedGiftIds) {
      const specData = spec[id]
      if (specData) {
        gifts.push({
          id,
          name: i18n[id] || id,
          tag: specData.tag as EGOGiftListItem['tag'],
          keyword: specData.keyword,
          attributeType: specData.attributeType,
          themePack: specData.themePack,
          maxEnhancement: specData.maxEnhancement,
        })
      }
    }
    return gifts
  }, [selectedGiftIds, spec, i18n])

  const hasSelectedGifts = selectedGifts.length > 0

  return (
    <PlannerSection title={t('pages.plannerMD.egoGiftObservation')} onViewNotes={onViewNotes}>
      {/* Cost display - right aligned */}
      <div className="flex justify-end mb-4">
        <StarlightCostDisplay cost={currentCost} size="lg" />
      </div>

      {/* Clickable content area */}
      <button
        type="button"
        onClick={onClick}
        className={cn(
          'w-full text-left',
          !readOnly && 'selectable cursor-pointer'
        )}
      >
        {hasSelectedGifts ? (
          <div className="flex flex-wrap gap-2 p-2 min-h-28">
            {selectedGifts.map((gift) => (
              <ScaledCardWrapper
                key={gift.id}
                cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
                cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
                mobileScale={mobileScale}
              >
                <EGOGiftCard gift={gift} />
              </ScaledCardWrapper>
            ))}
          </div>
        ) : (
          <div
            className={cn(
              'flex items-center justify-center p-2 text-muted-foreground',
              EMPTY_STATE.MIN_HEIGHT,
              EMPTY_STATE.DASHED_BORDER
            )}
          >
            {readOnly
              ? t('pages.plannerMD.emptyState.noEgoGifts')
              : t('pages.plannerMD.selectEgoGifts')}
          </div>
        )}
      </button>
    </PlannerSection>
  )
}
