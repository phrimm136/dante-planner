import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useShallow } from 'zustand/shallow'
import { useThemePackListData } from '@/hooks/useThemePackListData'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { toast } from 'sonner'
import { usePlannerEditorStoreSafe } from '@/stores/usePlannerEditorStore'
import { DifficultyIndicator, getFloorDifficultyLabel } from './DifficultyIndicator'
import { ThemePackViewer, ThemePackPlaceholder } from './ThemePackViewer'
import { ThemePackSelectorPane } from './ThemePackSelectorPane'
import { FloorGiftViewer } from './FloorGiftViewer'
import { FloorGiftSelectorPane } from './FloorGiftSelectorPane'
import { DUNGEON_IDX, type DungeonIdx, type MDCategory } from '@/lib/constants'
import { cn } from '@/lib/utils'
import { canSelectFloorThemePack, getUnaffordableGiftNames } from '@/lib/plannerHelpers'
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
  /** MD category for difficulty restrictions (optional, uses store if not provided) */
  category?: MDCategory
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
  category: categoryProp,
  readOnly = false,
  className,
  onViewNotes,
  floorSelectionsOverride,
  onThemePackSelectOverride,
  setSelectedGiftIdsOverride,
}: FloorThemeGiftSectionProps) {
  const { t } = useTranslation(['planner', 'common'])
  const { spec: themePackList, i18n: themePackI18n } = useThemePackListData()
  const { spec: egoGiftSpec, i18n: egoGiftI18n } = useEGOGiftListData()

  // Store state (safe - returns undefined if outside context)
  // Extract only the data needed for this floor to minimize re-renders
  const { allFloors, updateFloorSelection, storeCategory } = usePlannerEditorStoreSafe(
    useShallow((s) => ({
      currentFloor: s?.floorSelections?.[floorIndex],
      allFloors: s?.floorSelections ?? [],
      updateFloorSelection: s?.updateFloorSelection,
      storeCategory: s?.category,
    }))
  ) ?? { currentFloor: undefined, allFloors: [], updateFloorSelection: undefined, storeCategory: undefined }

  const floorSelections = floorSelectionsOverride ?? allFloors
  const category = categoryProp ?? storeCategory ?? '5F'

  // Handlers - use override if provided (tracker mode), otherwise use store action
  const handleThemePackSelect = (packId: string, difficulty: DungeonIdx) => {
    if (onThemePackSelectOverride) {
      onThemePackSelectOverride(packId, difficulty)
    } else if (updateFloorSelection) {
      // Preserve existing gifts
      const existingGifts = floorSelections[floorIndex]?.giftIds ?? new Set<string>()

      // Remove gifts that are unaffordable for the new theme pack
      let newGiftIds = existingGifts
      if (existingGifts.size > 0) {
        const { ids, names } = getUnaffordableGiftNames(existingGifts, packId, egoGiftSpec, egoGiftI18n)
        if (names.length > 0) {
          newGiftIds = new Set([...existingGifts].filter(id => !ids.includes(id)))
          toast.warning(t('pages.plannerMD.gifts.unaffordableWarning', { floor: floorNumber, gifts: names.join(', ') }))
        }
      }

      updateFloorSelection(floorIndex, {
        themePackId: packId,
        difficulty,
        giftIds: newGiftIds,
      })
    }
  }

  const handleGiftSelectionChange = (giftIds: Set<string>) => {
    if (setSelectedGiftIdsOverride) {
      setSelectedGiftIdsOverride(giftIds)
    } else if (updateFloorSelection && floorSelections[floorIndex]) {
      updateFloorSelection(floorIndex, {
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
      <div className={cn('flex flex-col [orientation:landscape]:flex-row sm:flex-row items-center [orientation:landscape]:items-start sm:items-start gap-4', className)}>
        <div className="flex flex-col w-56 h-104 items-center [orientation:landscape]:shrink-0 sm:shrink-0">
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
          category={category}
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
