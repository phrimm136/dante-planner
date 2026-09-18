import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/layout/PlannerSection'
import {
  EGOGiftCard,
  getBaseGiftId,
  toEGOGiftCardProps,
  toUnknownEGOGiftEntity,
} from '@/pages/egoGift'
import type { EncodedGiftId } from '@/shared/gameData'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/pages/egoGift'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import { CardSlot, EGO_GIFT_GEOMETRY, useSlotSizePx } from '@/shared/cardLayout'
import { CARD_MOBILE_SCALE, SECTION_STYLES } from '@/lib/constants'
import { EmptyStatePlaceholder } from '@/components/feedback/EmptyStatePlaceholder'
import {
  GIFT_ROW_PADDING_PX,
  giftRowMinHeightPx,
  KEYWORD_ICON_GEOMETRY,
} from '../../lib/cardLayout'
import { StartGiftKeywordIcon } from './StartGiftKeywordIcon'
import { cn } from '@/lib/utils'

export interface StartGiftSummaryProps {
  selectedKeyword: string | null
  selectedGiftIds: ReadonlySet<EncodedGiftId>
  onClick?: () => void
  readOnly?: boolean
  onViewNotes?: () => void
}

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
  const { heightPx: giftSlotHeightPx } = useSlotSizePx(EGO_GIFT_GEOMETRY.size, mobileScale)
  const minHeight = giftRowMinHeightPx(giftSlotHeightPx)

  const hasKeywordSelected = selectedKeyword !== null

  const selectedGifts = (() => {
    if (!hasKeywordSelected || !spec || !i18n || selectedGiftIds.size === 0) return []

    return Array.from(selectedGiftIds).map((encodedId) => {
      const giftId = getBaseGiftId(encodedId)
      const giftSpec = spec[giftId]
      const name = i18n[giftId] || `Gift ${giftId}`

      if (!giftSpec) return toUnknownEGOGiftEntity(giftId, name)

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
          <div
            className="flex items-center gap-4"
            style={{ padding: GIFT_ROW_PADDING_PX, minHeight }}
          >
            <CardSlot
              size={KEYWORD_ICON_GEOMETRY.size}
              mobileScale={mobileScale}
              className="shrink-0"
            >
              <StartGiftKeywordIcon keyword={selectedKeyword} />
            </CardSlot>

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
          <div className="flex" style={{ minHeight }}>
            <EmptyStatePlaceholder
              label={
                readOnly
                  ? t('pages.plannerMD.emptyState.noStartGifts')
                  : t('pages.plannerMD.selectStartEgoGift')
              }
              className="flex-1"
            />
          </div>
        )}
      </button>
    </PlannerSection>
  )
}

export type StoreBoundStartGiftSummaryProps = Omit<
  StartGiftSummaryProps,
  'selectedKeyword' | 'selectedGiftIds'
>

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
