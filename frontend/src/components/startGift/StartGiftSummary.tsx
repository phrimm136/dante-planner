import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/common/PlannerSection'
import { EGOGiftCard } from '@/components/egoGift/EGOGiftCard'
import { getStatusEffectIconPath } from '@/lib/assetPaths'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { cn } from '@/lib/utils'

interface StartGiftSummaryProps {
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

  // Show selected state when keyword is chosen (gifts are optional)
  const hasKeywordSelected = selectedKeyword !== null

  // Build gift objects for display
  const selectedGifts = useMemo(() => {
    if (!hasKeywordSelected || !spec || !i18n || selectedGiftIds.size === 0) return []

    return Array.from(selectedGiftIds).map((giftId) => {
      const giftSpec = spec[giftId]
      const giftName = i18n[giftId] || `Gift ${giftId}`

      return {
        id: giftId,
        name: giftName,
        tag: giftSpec?.tag || ['TIER_1'],
        keyword: giftSpec?.keyword || null,
        attributeType: giftSpec?.attributeType || 'CRIMSON',
        themePack: giftSpec?.themePack || [],
      }
    })
  }, [hasKeywordSelected, selectedGiftIds, spec, i18n])

  return (
    <PlannerSection title={t('pages.plannerMD.startEgoGift')} onViewNotes={onViewNotes}>
      <button
        type="button"
        onClick={onClick}
        className={cn(
          'w-full text-left',
          !readOnly && 'selectable cursor-pointer'
        )}
      >
        {hasKeywordSelected ? (
          /* Selected state: keyword icon + gift cards (if any) + EA counter */
          <div className="flex items-center gap-4 p-2 min-h-28">
            {/* Keyword icon */}
            <div className="w-16 h-16 flex items-center justify-center shrink-0">
              <img
                src={getStatusEffectIconPath(selectedKeyword)}
                alt={selectedKeyword}
                className="w-12 h-12 object-contain"
              />
            </div>

            {/* Selected gift cards (if any) */}
            <div className="flex flex-wrap gap-2">
              {selectedGifts.length > 0 ? (
                selectedGifts.map((gift) => (
                  <EGOGiftCard key={gift.id} gift={gift} />
                ))
              ) : (
                <span className="text-sm text-muted-foreground italic">
                  {t('pages.plannerMD.noEgoGiftSelected')}
                </span>
              )}
            </div>
          </div>
        ) : (
          /* Empty state: dashed border placeholder - min-h-28 matches selected state */
          <div className="flex items-center justify-center min-h-28 border-2 border-dashed border-muted-foreground/50 rounded-lg">
            <span className="text-muted-foreground">
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
