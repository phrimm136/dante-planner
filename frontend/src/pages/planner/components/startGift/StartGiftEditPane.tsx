import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { useStartGiftPools } from '../../hooks/useStartGiftPools'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/shared/egoGift'
import { encodeGiftSelection } from '@/pages/egoGift'
import type { EGOGiftId } from '@/shared/gameData'
import { useStartBuffListSpec, useStartBuffListI18n } from '../../hooks/useStartBuffList'
import { toStartBuffs } from '../../lib/startBuffs'
import { useCappedSelection } from '../../hooks/useCappedSelection'
import { usePlannerStore, useStoreSeason } from '../../stores/usePlannerStore'
import { calculateMaxGiftSelection } from '../../lib/startGiftCalculator'
import { SelectorPaneShell } from '../SelectorPaneShell'
import { StartGiftRow } from './StartGiftRow'
import type { EGOGiftSpec, EGOGiftNameList } from '@/shared/egoGift'
import { SECTION_STYLES } from '@/lib/constants'

interface StartGiftEditPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function StartGiftEditPane({ open, onOpenChange }: StartGiftEditPaneProps) {
  const { contentVersion } = useStoreSeason()
  const selectedBuffIds = usePlannerStore((s) => s.selectedBuffIds)
  const selectedKeyword = usePlannerStore((s) => s.selectedGiftKeyword)
  const selectedGiftIds = usePlannerStore((s) => s.selectedGiftIds)
  const setSelectedKeyword = usePlannerStore((s) => s.setSelectedGiftKeyword)
  const setSelectedGiftIds = usePlannerStore((s) => s.setSelectedGiftIds)
  const comprehensiveGiftIds = usePlannerStore((s) => s.comprehensiveGiftIds)
  const setComprehensiveGiftIds = usePlannerStore((s) => s.setComprehensiveGiftIds)
  const { t } = useTranslation(['planner', 'common'])

  const { data: pools } = useStartGiftPools(contentVersion)
  const spec = useEGOGiftListSpec()
  const i18n = useEGOGiftListI18n()
  const buffs = toStartBuffs(
    useStartBuffListSpec(contentVersion),
    useStartBuffListI18n(contentVersion),
  )

  const maxSelectable = calculateMaxGiftSelection(buffs, selectedBuffIds)

  const { toggle, clear } = useCappedSelection({
    cap: maxSelectable,
    selected: selectedGiftIds,
    onSelectedChange: setSelectedGiftIds,
    mirror: comprehensiveGiftIds,
    onMirrorChange: setComprehensiveGiftIds,
  })

  const handleRowSelect = (keyword: string) => {
    clear()
    setSelectedKeyword(selectedKeyword === keyword ? null : keyword)
  }

  const handleGiftClick = (rowKeyword: string, giftId: EGOGiftId) => {
    const encodedId = encodeGiftSelection(0, giftId)
    if (selectedKeyword !== rowKeyword) {
      const newComprehensive = new Set(comprehensiveGiftIds)
      for (const id of selectedGiftIds) {
        newComprehensive.delete(id)
      }
      newComprehensive.add(encodedId)
      setComprehensiveGiftIds(newComprehensive)
      setSelectedKeyword(rowKeyword)
      setSelectedGiftIds(new Set([encodedId]))
      return
    }

    toggle(encodedId)
  }

  const keywordPools = Object.entries(pools)

  return (
    <SelectorPaneShell
      open={open}
      onOpenChange={onOpenChange}
      title={t('pages.plannerMD.startEgoGift')}
      headerActions={
        <>
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
      <div className="space-y-2">
        {keywordPools.map(([keyword, giftIds]) => (
          <StartGiftRow
            key={keyword}
            keyword={keyword}
            giftIds={giftIds}
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
