import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { useStartGiftPools } from '../../hooks/useStartGiftPools'
import { useEGOGiftListData } from '@/pages/egoGift'
import { useStartBuffData } from '../../hooks/useStartBuffData'
import { useCappedSelection } from '../../hooks/useCappedSelection'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import type { MDVersion } from '@/shared/gameData'
import { calculateMaxGiftSelection } from '../../lib/startGiftCalculator'
import { SelectorPaneShell } from '../SelectorPaneShell'
import { StartGiftRow } from './StartGiftRow'
import type { EGOGiftSpec, EGOGiftNameList } from '@/pages/egoGift'
import { SECTION_STYLES } from '@/lib/constants'

interface StartGiftEditPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  mdVersion: MDVersion
}

/**
 * Dialog for editing start gift selection
 * - Displays 10 keyword rows (vertical)
 * - Each row has keyword icon + 3 gift cards (horizontal)
 * - Single keyword selection at a time
 * - Gift selection gated by keyword selection
 * - Selection count = 1 + ADDITIONAL_START_EGO_GIFT_SELECT effects
 */
export function StartGiftEditPane({ open, onOpenChange, mdVersion }: StartGiftEditPaneProps) {
  // Store state
  const selectedBuffIds = usePlannerEditorStore((s) => s.selectedBuffIds)
  const selectedKeyword = usePlannerEditorStore((s) => s.selectedGiftKeyword)
  const selectedGiftIds = usePlannerEditorStore((s) => s.selectedGiftIds)
  const setSelectedKeyword = usePlannerEditorStore((s) => s.setSelectedGiftKeyword)
  const setSelectedGiftIds = usePlannerEditorStore((s) => s.setSelectedGiftIds)
  const comprehensiveGiftIds = usePlannerEditorStore((s) => s.comprehensiveGiftIds)
  const setComprehensiveGiftIds = usePlannerEditorStore((s) => s.setComprehensiveGiftIds)
  const { t } = useTranslation(['planner', 'common'])

  // Load data
  const { data: pools } = useStartGiftPools(mdVersion)
  const { spec, i18n } = useEGOGiftListData()
  const { data: buffs } = useStartBuffData(mdVersion)

  // Calculate max selectable gifts
  const maxSelectable = calculateMaxGiftSelection(buffs, selectedBuffIds)

  const { toggle, clear } = useCappedSelection({
    cap: maxSelectable,
    selected: selectedGiftIds,
    onSelectedChange: setSelectedGiftIds,
    mirror: comprehensiveGiftIds,
    onMirrorChange: setComprehensiveGiftIds,
  })

  // Row click (not gift) - just toggle row selection
  const handleRowSelect = (keyword: string) => {
    clear()
    setSelectedKeyword(selectedKeyword === keyword ? null : keyword)
  }

  // Gift click - combined row + gift selection in ONE update
  const handleGiftClick = (rowKeyword: string, giftId: string) => {
    // Different row - select row AND gift together
    if (selectedKeyword !== rowKeyword) {
      const newComprehensive = new Set(comprehensiveGiftIds)
      // Remove old row's gifts from comprehensive
      for (const id of selectedGiftIds) {
        newComprehensive.delete(id)
      }
      // Add new gift to comprehensive
      newComprehensive.add(giftId)
      setComprehensiveGiftIds(newComprehensive)
      setSelectedKeyword(rowKeyword)
      setSelectedGiftIds(new Set([giftId]))
      return
    }

    toggle(giftId)
  }

  const keywords = Object.keys(pools)

  return (
    <SelectorPaneShell
      open={open}
      onOpenChange={onOpenChange}
      title={t('pages.plannerMD.startEgoGift')}
      headerActions={
        <>
          {/* EA Counter */}
          <span className={SECTION_STYLES.TEXT.caption}>
            {t('pages.plannerMD.egoGiftSelection')}: {selectedGiftIds.size}/{maxSelectable}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              clear()
              setSelectedKeyword(null)
            }}
          >
            {t('common:reset')}
          </Button>
        </>
      }
    >
      {/* 10 Keyword Rows */}
      <div className="space-y-2">
        {keywords.map((keyword) => (
          <StartGiftRow
            key={keyword}
            keyword={keyword}
            giftIds={pools[keyword]}
            giftSpecMap={spec as Record<string, EGOGiftSpec>}
            giftNameMap={i18n as EGOGiftNameList}
            isRowSelected={selectedKeyword === keyword}
            selectedGiftIds={selectedGiftIds}
            maxSelectable={maxSelectable}
            onRowSelect={handleRowSelect}
            onGiftClick={handleGiftClick}
          />
        ))}
      </div>
    </SelectorPaneShell>
  )
}
