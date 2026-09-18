import { useState, useEffect, useRef, startTransition, Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { LoadingState } from '@/components/feedback/LoadingState'

import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { applyGiftToggle } from '../../lib/giftToggle'
import type { EGOGiftEntity } from '@/pages/egoGift'
import type { EGOGiftId, EnhancementLevel } from '@/shared/gameData'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/pages/egoGift'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import { sortEGOGifts } from '@/pages/egoGift'
import { EGOGiftFilterBar } from '@/pages/egoGift'
import { EGOGiftSelectionList } from '@/pages/egoGift'
import type { SortMode } from '@/shared/filter'
import { SECTION_STYLES } from '@/lib/constants'
import { toEGOGiftEntity } from '@/pages/egoGift'

interface ComprehensiveGiftSelectorPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ComprehensiveGiftSelectorPane({
  open,
  onOpenChange,
}: ComprehensiveGiftSelectorPaneProps) {
  const selectedGiftIds = usePlannerEditorStore((s) => s.comprehensiveGiftIds)
  const setComprehensiveGiftIds = usePlannerEditorStore((s) => s.setComprehensiveGiftIds)
  const { t } = useTranslation(['planner', 'common'])
  const spec = useEGOGiftListSpec()
  const i18n = useEGOGiftListI18n()

  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

  useEffect(() => {
    if (!open) {
      setSelectedKeywords(new Set())
      setSearchQuery('')
      setSortMode('tier-first')
    }
  }, [open])

  const gifts: EGOGiftEntity[] = (() => {
    return Object.entries(spec).map(([id, entry]) => toEGOGiftEntity(id, entry, i18n[id] || id))
  })()

  const specById = (() => {
    return new Map(Object.entries(spec))
  })()

  const sortedGifts = (() => {
    return sortEGOGifts(gifts, sortMode)
  })()

  // Read through a ref so the handler keeps one identity for the pane's lifetime.
  // Closing over the selection would give every gift cell a new callback on each
  // toggle, re-rendering all of them to change one card.
  const latest = useRef({ selectedGiftIds, specById, setComprehensiveGiftIds })
  useEffect(() => {
    latest.current = { selectedGiftIds, specById, setComprehensiveGiftIds }
  })

  const [handleEnhancementSelect] = useState(
    () => (giftId: EGOGiftId, enhancement: EnhancementLevel) => {
      startTransition(() => {
        const {
          selectedGiftIds: current,
          specById: specs,
          setComprehensiveGiftIds: notify,
        } = latest.current

        notify(applyGiftToggle(current, giftId, enhancement, { specById: specs }))
      })
    },
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] flex flex-col"
        showCloseButton={false}
      >
        <DialogHeader>
          <div className={SECTION_STYLES.LAYOUT.rowBetween}>
            <DialogTitle>{t('pages.plannerMD.comprehensiveEgoGiftList')}</DialogTitle>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setComprehensiveGiftIds(new Set())
                }}
              >
                {t('common:reset')}
              </Button>
              <Button
                size="sm"
                onClick={() => {
                  onOpenChange(false)
                }}
              >
                {t('common:done')}
              </Button>
            </div>
          </div>
        </DialogHeader>

        <EGOGiftFilterBar
          className="py-2"
          selectedKeywords={selectedKeywords}
          onKeywordsChange={setSelectedKeywords}
          sortMode={sortMode}
          onSortModeChange={setSortMode}
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
        />

        <div className="flex-1 overflow-y-auto">
          <Suspense fallback={<LoadingState />}>
            <EGOGiftSelectionList
              gifts={sortedGifts}
              selectedKeywords={selectedKeywords}
              searchQuery={searchQuery}
              selectedGiftIds={selectedGiftIds}
              enableEnhancementSelection
              onEnhancementSelect={handleEnhancementSelect}
            />
          </Suspense>
        </div>
      </DialogContent>
    </Dialog>
  )
}
