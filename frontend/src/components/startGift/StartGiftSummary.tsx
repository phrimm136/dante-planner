import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/common/PlannerSection'
import { EGOGiftCard } from '@/components/egoGift/EGOGiftCard'
import { getStatusEffectIconPath } from '@/lib/assetPaths'
import { calculateMaxGiftSelection } from '@/lib/startGiftCalculator'
import { useStartBuffData, type MDVersion } from '@/hooks/useStartBuffData'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'

interface StartGiftSummaryProps {
  mdVersion: MDVersion
  selectedBuffIds: Set<number>
  selectedKeyword: string | null
  selectedGiftIds: Set<string>
  onClick?: () => void
}

/**
 * Summary view for start gift selection.
 * Shows selected keyword + gift cards when selection exists,
 * or a dashed border placeholder when empty.
 * Clicking opens the StartGiftEditPane dialog.
 */
export function StartGiftSummary({
  mdVersion,
  selectedBuffIds,
  selectedKeyword,
  selectedGiftIds,
  onClick,
}: StartGiftSummaryProps) {
  const { t } = useTranslation()
  const { data: buffs } = useStartBuffData(mdVersion)
  const { spec, i18n } = useEGOGiftListData()

  // Calculate max selectable gifts
  const maxSelectable = useMemo(
    () => calculateMaxGiftSelection(buffs, selectedBuffIds),
    [buffs, selectedBuffIds]
  )

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
    <PlannerSection title={t('pages.plannerMD.startGift')}>
      <div
        className="cursor-pointer hover:opacity-90 transition-opacity"
        onClick={onClick}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') onClick?.()
        }}
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
                  <EGOGiftCard key={gift.id} gift={gift} isSelected />
                ))
              ) : (
                <span className="text-sm text-muted-foreground italic">
                  {t('pages.plannerMD.noGiftSelected')}
                </span>
              )}
            </div>

            {/* EA counter */}
            <div className="ml-auto text-sm text-muted-foreground shrink-0">
              {selectedGiftIds.size}/{maxSelectable}
            </div>
          </div>
        ) : (
          /* Empty state: dashed border placeholder - min-h-28 matches selected state */
          <div className="flex flex-col items-center justify-center min-h-28 border-2 border-dashed border-muted-foreground/50 rounded-lg hover:border-primary hover:bg-muted/20 transition-colors">
            <span className="text-muted-foreground">
              {t('pages.plannerMD.selectStartGift')}
            </span>
            <span className="text-xs text-muted-foreground mt-1">
              0/{maxSelectable}
            </span>
          </div>
        )}
      </div>
    </PlannerSection>
  )
}
