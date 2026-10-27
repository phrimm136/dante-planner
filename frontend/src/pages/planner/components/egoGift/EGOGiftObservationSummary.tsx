import { useTranslation } from 'react-i18next'
import { useEGOGiftObservationData } from '@/pages/egoGift'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/shared/egoGift'
import { getBaseGiftId } from '@/pages/egoGift'
import type { EncodedGiftId } from '@/shared/gameData'
import { usePlannerStore, useStoreSeason } from '../../stores/usePlannerStore'
import { CARD_MOBILE_SCALE } from '@/lib/constants'
import { cn } from '@/lib/utils'
import { EmptyStatePlaceholder } from '@/components/feedback/EmptyStatePlaceholder'
import { GIFT_ROW_PADDING_PX, giftRowMinHeightPx } from '../../lib/cardLayout'
import type { EGOGiftEntity } from '@/shared/egoGift'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { StarlightCostDisplay } from '../StarlightCostDisplay'
import { CardSlot, EGO_GIFT_GEOMETRY, useSlotSizePx } from '@/shared/cardLayout'
import { EGOGiftCard } from '@/shared/egoGift'
import { toEGOGiftEntity } from '@/shared/egoGift'

export interface EGOGiftObservationSummaryProps {
  selectedGiftIds: ReadonlySet<EncodedGiftId>
  onClick?: () => void
  readOnly?: boolean
  onViewNotes?: () => void
}

export function EGOGiftObservationSummary({
  selectedGiftIds,
  onClick,
  readOnly = false,
  onViewNotes,
}: EGOGiftObservationSummaryProps) {
  const { t } = useTranslation(['planner', 'common'])

  const mobileScale = CARD_MOBILE_SCALE
  const { heightPx: giftSlotHeightPx } = useSlotSizePx(EGO_GIFT_GEOMETRY.size, mobileScale)
  const minHeight = giftRowMinHeightPx(giftSlotHeightPx)

  const { contentVersion } = useStoreSeason()
  const { data: observationData } = useEGOGiftObservationData(contentVersion)
  const spec = useEGOGiftListSpec()
  const i18n = useEGOGiftListI18n()

  const currentCost =
    observationData.observationEgoGiftCostDataList.find(
      (cost) => cost.egogiftCount === selectedGiftIds.size,
    )?.starlightCost || 0

  const selectedGifts: EGOGiftEntity[] = (() => {
    const gifts: EGOGiftEntity[] = []
    for (const id of selectedGiftIds) {
      const baseId = getBaseGiftId(id)
      const specData = spec[baseId]
      if (specData) gifts.push(toEGOGiftEntity(baseId, specData, i18n[baseId] || baseId))
    }
    return gifts
  })()

  const hasSelectedGifts = selectedGifts.length > 0

  return (
    <PlannerSection
      title={t('pages.plannerMD.egoGiftObservation')}
      {...(onViewNotes !== undefined && { onViewNotes })}
    >
      <div className="flex justify-end mb-4">
        <StarlightCostDisplay cost={currentCost} size="lg" />
      </div>

      <button
        type="button"
        onClick={onClick}
        className={cn('w-full text-left', !readOnly && 'selectable cursor-pointer')}
      >
        {hasSelectedGifts ? (
          <div className="flex flex-wrap gap-2" style={{ padding: GIFT_ROW_PADDING_PX, minHeight }}>
            {selectedGifts.map((gift) => (
              <CardSlot key={gift.id} size={EGO_GIFT_GEOMETRY.size} mobileScale={mobileScale}>
                <EGOGiftCard gift={gift} />
              </CardSlot>
            ))}
          </div>
        ) : (
          <div className="flex" style={{ minHeight }}>
            <EmptyStatePlaceholder
              label={
                readOnly
                  ? t('pages.plannerMD.emptyState.noEgoGifts')
                  : t('pages.plannerMD.selectEgoGifts')
              }
              className="flex-1"
            />
          </div>
        )}
      </button>
    </PlannerSection>
  )
}

export type StoreBoundEGOGiftObservationSummaryProps = Omit<
  EGOGiftObservationSummaryProps,
  'selectedGiftIds'
>

export function StoreBoundEGOGiftObservationSummary(
  props: StoreBoundEGOGiftObservationSummaryProps,
) {
  const selectedGiftIds = usePlannerStore((s) => s.observationGiftIds)

  return <EGOGiftObservationSummary {...props} selectedGiftIds={selectedGiftIds} />
}
