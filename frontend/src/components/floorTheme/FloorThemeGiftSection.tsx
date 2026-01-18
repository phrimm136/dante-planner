import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useThemePackListData } from '@/hooks/useThemePackListData'
import { usePlannerEditorStoreSafe } from '@/stores/usePlannerEditorStore'
import { DifficultyIndicator, getFloorDifficultyLabel } from './DifficultyIndicator'
import { ThemePackViewer, ThemePackPlaceholder } from './ThemePackViewer'
import { ThemePackSelectorPane } from './ThemePackSelectorPane'
import { FloorGiftViewer } from './FloorGiftViewer'
import { FloorGiftSelectorPane } from './FloorGiftSelectorPane'
import { DUNGEON_IDX, type DungeonIdx } from '@/lib/constants'
import { cn } from '@/lib/utils'
import { canSelectFloorThemePack } from '@/lib/plannerHelpers'
import { PlannerSection } from '@/components/common/PlannerSection'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import type { FloorThemeSelection } from '@/types/ThemePackTypes'

interface FloorThemeGiftSectionProps {
  floorNumber: number // 1-indexed (1-15)
  floorIndex: number // 0-indexed (0-14)
  readOnly?: boolean
  className?: string
  onViewNotes?: () => void
  /** Override floorSelections from store (for tracker mode) */
  floorSelectionsOverride?: FloorThemeSelection[]
  /** Override handler for theme pack selection (for tracker mode) */
  onThemePackSelectOverride?: (packId: string, difficulty: DungeonIdx) => void
  /** Override handler for gift selection (for tracker mode) */
  setSelectedGiftIdsOverride?: (giftIds: Set<string>) => void
}

/**
 * Container for a single floor's theme pack and gift selection
 * Layout: Floor label | Difficulty indicator | Theme pack viewer | Gift viewer
 */
export function FloorThemeGiftSection({
  floorNumber,
  floorIndex,
  readOnly = false,
  className,
  onViewNotes,
  floorSelectionsOverride,
  onThemePackSelectOverride,
  setSelectedGiftIdsOverride,
}: FloorThemeGiftSectionProps) {
  const { t } = useTranslation(['planner', 'common'])
  const { spec: themePackList, i18n: themePackI18n } = useThemePackListData()

  // Store state (safe - returns undefined if outside context)
  const storeFloorSelections = usePlannerEditorStoreSafe((s) => s.floorSelections)
  const storeUpdateFloorSelection = usePlannerEditorStoreSafe((s) => s.updateFloorSelection)
  const floorSelections = floorSelectionsOverride ?? storeFloorSelections!

  // Handlers - use override if provided (tracker mode), otherwise use store action
  const handleThemePackSelect = (packId: string, difficulty: DungeonIdx) => {
    if (onThemePackSelectOverride) {
      onThemePackSelectOverride(packId, difficulty)
    } else if (storeUpdateFloorSelection) {
      storeUpdateFloorSelection(floorIndex, {
        themePackId: packId,
        difficulty,
        giftIds: new Set<string>(),
      })
    }
  }

  const handleGiftSelectionChange = (giftIds: Set<string>) => {
    if (setSelectedGiftIdsOverride) {
      setSelectedGiftIdsOverride(giftIds)
    } else if (storeUpdateFloorSelection && floorSelections[floorIndex]) {
      storeUpdateFloorSelection(floorIndex, {
        ...floorSelections[floorIndex],
        giftIds,
      })
    }
  }

  // Derived state from floor selections
  const selection = floorSelections[floorIndex]
  const selectedThemePackId = selection?.themePackId ?? null
  const selectedDifficulty = selection?.difficulty ?? null
  const selectedGiftIds = selection?.giftIds ?? new Set<string>()
  const previousFloorDifficulty = floorIndex > 0 ? floorSelections[floorIndex - 1]?.difficulty ?? null : null

  const [isThemePackPaneOpen, setIsThemePackPaneOpen] = useState(false)
  const [isGiftPaneOpen, setIsGiftPaneOpen] = useState(false)

  // Check if theme pack selector should be readOnly
  const isThemePackReadOnly = readOnly || !canSelectFloorThemePack(floorIndex, floorSelections)

  // Check if gift selector should be readOnly (no theme pack selected)
  const isGiftReadOnly = readOnly || !selectedThemePackId

  // Get the selected theme pack entry and name
  const selectedPackEntry = selectedThemePackId ? themePackList[selectedThemePackId] : null
  const selectedPackI18n = selectedThemePackId ? themePackI18n[selectedThemePackId] : null
  const selectedPackName = selectedPackI18n?.name ?? null
  const selectedPackSpecialName = selectedPackI18n?.specialName

  // Get display difficulty label - map DungeonIdx to baseDifficulty for label calculation
  // For floors 6-15, getFloorDifficultyLabel returns INFINITY/EXTREME regardless of baseDifficulty
  const getBaseDifficulty = (dungeonIdx: DungeonIdx): 'NORMAL' | 'HARD' => {
    return dungeonIdx === DUNGEON_IDX.NORMAL ? 'NORMAL' : 'HARD'
  }
  const difficultyLabel = selectedDifficulty !== null
    ? getFloorDifficultyLabel(floorNumber, getBaseDifficulty(selectedDifficulty))
    : null

  const handleOpenThemePackPane = () => {
    setIsThemePackPaneOpen(true)
  }

  const handleOpenGiftPane = () => {
    // Only open gift pane if theme pack is selected
    if (selectedThemePackId) {
      setIsGiftPaneOpen(true)
    }
  }

  // Memoize used theme pack IDs to prevent unnecessary child re-renders
  const usedThemePackIds = useMemo(() => {
    const ids = new Set<string>()
    for (let i = 0; i < floorSelections.length; i++) {
      if (i === floorIndex) continue
      const floorThemePackId = floorSelections[i].themePackId
      if (floorThemePackId) {
        ids.add(floorThemePackId)
      }
    }
    return ids
  }, [floorSelections, floorIndex])

  return (
    <PlannerSection title={t('pages.plannerMD.floor', { number: floorNumber })} onViewNotes={onViewNotes}>
      <div className={cn('flex items-start gap-4', className)}>
        <div className="flex flex-col w-56 h-104 items-center">
          {/* Difficulty indicator */}
          <DifficultyIndicator difficulty={difficultyLabel} />

          {/* Theme pack viewer */}
          <div className="shrink-0">
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  {selectedThemePackId && selectedPackEntry && selectedPackName ? (
                    <ThemePackViewer
                      packId={selectedThemePackId}
                      packEntry={selectedPackEntry}
                      packName={selectedPackName}
                      specialName={selectedPackSpecialName}
                      onClick={handleOpenThemePackPane}
                      readOnly={isThemePackReadOnly}
                      enableHoverHighlight
                    />
                  ) : (
                    <ThemePackPlaceholder
                      onClick={handleOpenThemePackPane}
                      readOnly={isThemePackReadOnly}
                    />
                  )}
                </TooltipTrigger>
                {isThemePackReadOnly && !readOnly && (
                  <TooltipContent>
                    <p>{t('pages.plannerMD.previousFloorNoThemePack')}</p>
                  </TooltipContent>
                )}
              </Tooltip>
            </TooltipProvider>
          </div>
        </div>

        {/* Gift viewer */}
        <div className="flex-1 mt-5 min-w-0">
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <FloorGiftViewer
                  selectedGiftIds={selectedGiftIds}
                  onClick={handleOpenGiftPane}
                  readOnly={isGiftReadOnly}
                />
              </TooltipTrigger>
              {isGiftReadOnly && !readOnly && (
                <TooltipContent>
                  <p>{t('pages.plannerMD.selectThemePackFirst')}</p>
                </TooltipContent>
              )}
            </Tooltip>
          </TooltipProvider>
        </div>

        {/* Theme pack selector pane */}
        <ThemePackSelectorPane
          open={isThemePackPaneOpen}
          onOpenChange={setIsThemePackPaneOpen}
          floorNumber={floorNumber}
          previousFloorDifficulty={previousFloorDifficulty}
          themePackList={themePackList}
          themePackI18n={themePackI18n}
          onSelect={handleThemePackSelect}
          usedThemePackIds={usedThemePackIds}
        />

        {/* Gift selector pane */}
        {selectedThemePackId && selectedDifficulty !== null && (
          <FloorGiftSelectorPane
            open={isGiftPaneOpen}
            onOpenChange={setIsGiftPaneOpen}
            floorNumber={floorNumber}
            themePackId={selectedThemePackId}
            difficulty={selectedDifficulty}
            selectedGiftIds={selectedGiftIds}
            onGiftSelectionChange={handleGiftSelectionChange}
          />
        )}
      </div>
    </PlannerSection>
  )
}
