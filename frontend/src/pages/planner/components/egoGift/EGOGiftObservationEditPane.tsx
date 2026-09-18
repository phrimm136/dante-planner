import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { useEGOGiftObservationData } from '@/pages/egoGift'
import { useEGOGiftListSpec, useEGOGiftListI18n, encodeGiftSelection } from '@/pages/egoGift'
import type { EGOGiftId } from '@/shared/gameData'
import { useCappedSelection } from '../../hooks/useCappedSelection'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import type { EGOGiftEntity } from '@/pages/egoGift'
import type { SortMode } from '@/shared/filter'
import { EGOGiftFilterBar } from '@/pages/egoGift'
import { SelectorPaneShell } from '../SelectorPaneShell'
import { StarlightCostDisplay } from '../StarlightCostDisplay'
import { sortEGOGifts } from '@/pages/egoGift'
import { EGOGiftSelectionList } from '@/pages/egoGift'
import { EGOGiftObservationSelection } from '@/pages/egoGift'
import { MAX_OBSERVABLE_GIFTS } from '@/shared/gameData'
import { toEGOGiftEntity } from '@/pages/egoGift'

interface EGOGiftObservationEditPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  mdVersion: number
}

export function EGOGiftObservationEditPane({
  open,
  onOpenChange,
  mdVersion,
}: EGOGiftObservationEditPaneProps) {
  const selectedGiftIds = usePlannerEditorStore((s) => s.observationGiftIds)
  const setObservationGiftIds = usePlannerEditorStore((s) => s.setObservationGiftIds)
  const comprehensiveGiftIds = usePlannerEditorStore((s) => s.comprehensiveGiftIds)
  const setComprehensiveGiftIds = usePlannerEditorStore((s) => s.setComprehensiveGiftIds)
  const { t } = useTranslation(['planner', 'common'])

  const { data: observationData } = useEGOGiftObservationData(mdVersion)
  const spec = useEGOGiftListSpec()
  const i18n = useEGOGiftListI18n()

  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

  const gifts: EGOGiftEntity[] = (() => {
    return Object.entries(spec).map(([id, entry]) => toEGOGiftEntity(id, entry, i18n[id] || id))
  })()

  const sortedGifts = (() => {
    let filtered = gifts
    if (observationData.observationEgoGiftDataList.length > 0) {
      const idSet = new Set(observationData.observationEgoGiftDataList.map(String))
      filtered = filtered.filter((gift) => idSet.has(gift.id))
    }
    return sortEGOGifts(filtered, sortMode)
  })()

  useEffect(() => {
    if (!open) {
      setSelectedKeywords(new Set())
      setSearchQuery('')
      setSortMode('tier-first')
    }
  }, [open])

  const { toggle, clear } = useCappedSelection({
    cap: MAX_OBSERVABLE_GIFTS,
    selected: selectedGiftIds,
    onSelectedChange: setObservationGiftIds,
    mirror: comprehensiveGiftIds,
    onMirrorChange: setComprehensiveGiftIds,
  })

  const handleGiftToggle = (giftId: EGOGiftId) => {
    toggle(encodeGiftSelection(0, giftId))
  }

  const currentCost =
    observationData.observationEgoGiftCostDataList.find(
      (cost) => cost.egogiftCount === selectedGiftIds.size,
    )?.starlightCost || 0

  return (
    <SelectorPaneShell
      open={open}
      onOpenChange={onOpenChange}
      title={t('pages.plannerMD.egoGiftObservation')}
      headerActions={
        <>
          <StarlightCostDisplay cost={currentCost} size="lg" />
          <Button variant="outline" size="sm" onClick={clear}>
            {t('common:reset')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <EGOGiftFilterBar
          selectedKeywords={selectedKeywords}
          onKeywordsChange={setSelectedKeywords}
          sortMode={sortMode}
          onSortModeChange={setSortMode}
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
        />

        <div className="flex flex-col sm:flex-row gap-2">
          <div className="flex-1 min-w-0">
            <EGOGiftSelectionList
              gifts={sortedGifts}
              selectedKeywords={selectedKeywords}
              searchQuery={searchQuery}
              selectedGiftIds={selectedGiftIds}
              onGiftSelect={handleGiftToggle}
            />
          </div>

          <div className="sm:shrink-0">
            <EGOGiftObservationSelection
              selectedGiftIds={Array.from(selectedGiftIds)}
              onGiftRemove={toggle}
            />
          </div>
        </div>
      </div>
    </SelectorPaneShell>
  )
}
