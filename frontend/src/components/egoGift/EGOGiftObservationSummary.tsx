import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOGiftObservationData } from '@/hooks/useEGOGiftObservationData'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { EMPTY_STATE } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import { PlannerSection } from '@/components/common/PlannerSection'
import { StarlightCostDisplay } from '@/components/common/StarlightCostDisplay'
import { EGOGiftCard } from './EGOGiftCard'

interface EGOGiftObservationSummaryProps {
  selectedGiftIds: Set<string>
  onClick?: () => void
  readOnly?: boolean
  onViewNotes?: () => void
}

/**
 * EGO Gift Observation Summary component.
 * Displays selected gifts horizontally with cost. Clicking opens EditPane.
 * Pattern: StartBuffSection (clickable summary with cost right-aligned)
 * Suspends while loading - wrap in Suspense boundary
 */
export function EGOGiftObservationSummary({
  selectedGiftIds,
  onClick,
  readOnly = false,
  onViewNotes,
}: EGOGiftObservationSummaryProps) {
  const { t } = useTranslation(['planner', 'common'])

  // Load observation data for cost calculation (suspends)
  const { data: observationData } = useEGOGiftObservationData()
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
              <EGOGiftCard key={gift.id} gift={gift} isSelected />
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
