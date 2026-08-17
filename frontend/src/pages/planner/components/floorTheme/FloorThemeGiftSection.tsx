import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useShallow } from 'zustand/shallow'
import { useThemePackListData } from '@/pages/themePack'
import { useEGOGiftListData } from '@/pages/egoGift'
import { showWarning } from '@/lib/errorPresentation'
import { usePlannerEditorStoreSafe } from '../../stores/usePlannerEditorStore'
import { DifficultyIndicator, getFloorDifficultyLabel } from './DifficultyIndicator'
import { ThemePackViewer, ThemePackPlaceholder } from './ThemePackViewer'
import { ThemePackSelectorPane } from './ThemePackSelectorPane'
import { FloorGiftViewer } from './FloorGiftViewer'
import { FloorGiftSelectorPane } from './FloorGiftSelectorPane'
import { DUNGEON_IDX, type DungeonIdx, type MDCategory } from '@/shared/gameData'
import { cn } from '@/lib/utils'
import { canSelectFloorThemePack, getUnaffordableGiftNames } from '../../lib/plannerRules'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import type { FloorThemeSelection } from '@/pages/themePack'
import { ThemePackIdSchema } from '@/shared/gameData'

/** Shared empty result so a closed picker's selector keeps one identity. */
const EMPTY_PACK_IDS: string[] = []

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

  const [isThemePackPaneOpen, setIsThemePackPaneOpen] = useState(false)
  const [isGiftPaneOpen, setIsGiftPaneOpen] = useState(false)

  // Only this floor's own selection plus the two cross-floor facts it reads: the
  // previous floor's difficulty and whether that floor has a pack at all. Every
  // member is a primitive or an entry whose identity survives a sibling's edit.
  const storeSlice = usePlannerEditorStoreSafe(
    useShallow((s) => ({
      selection: s?.floorSelections?.[floorIndex],
      previousDifficulty:
        floorIndex > 0 ? (s?.floorSelections?.[floorIndex - 1]?.difficulty ?? null) : null,
      previousHasThemePack:
        floorIndex === 0 || (s?.floorSelections?.[floorIndex - 1]?.themePackId ?? null) !== null,
      updateFloorSelection: s?.updateFloorSelection,
      storeCategory: s?.category,
    })),
  )

  // Which packs the other floors occupy is read only while the picker is open,
  // so a closed picker never subscribes to a sibling floor.
  const usedThemePackIdsFromStore = usePlannerEditorStoreSafe(
    useShallow((s) =>
      isThemePackPaneOpen && !floorSelectionsOverride
        ? s.floorSelections.flatMap((floor, i) =>
            i !== floorIndex && floor.themePackId ? [floor.themePackId] : [],
          )
        : EMPTY_PACK_IDS,
    ),
  )

  const updateFloorSelection = storeSlice?.updateFloorSelection
  const category = categoryProp ?? storeSlice?.storeCategory ?? '5F'

  // Tracker mode drives the whole floor list in as a prop; the editor reads the store.
  const selection = floorSelectionsOverride
    ? floorSelectionsOverride[floorIndex]
    : storeSlice?.selection
  const previousFloorDifficulty = floorSelectionsOverride
    ? floorIndex > 0
      ? (floorSelectionsOverride[floorIndex - 1]?.difficulty ?? null)
      : null
    : (storeSlice?.previousDifficulty ?? null)
  const canSelectThemePack = floorSelectionsOverride
    ? canSelectFloorThemePack(floorIndex, floorSelectionsOverride)
    : (storeSlice?.previousHasThemePack ?? true)
  const usedThemePackIds = new Set(
    floorSelectionsOverride
      ? floorSelectionsOverride.flatMap((floor, i) =>
          i !== floorIndex && floor.themePackId ? [floor.themePackId] : [],
        )
      : (usedThemePackIdsFromStore ?? EMPTY_PACK_IDS),
  )

  // Handlers - use override if provided (tracker mode), otherwise use store action
  const handleThemePackSelect = (packId: string, difficulty: DungeonIdx) => {
    if (onThemePackSelectOverride) {
      onThemePackSelectOverride(packId, difficulty)
    } else if (updateFloorSelection) {
      // Preserve existing gifts
      const existingGifts = selection?.giftIds ?? new Set<string>()

      // Remove gifts that are unaffordable for the new theme pack
      let newGiftIds = existingGifts
      if (existingGifts.size > 0) {
        const { ids, names } = getUnaffordableGiftNames(
          existingGifts,
          packId,
          egoGiftSpec,
          egoGiftI18n,
        )
        if (names.length > 0) {
          newGiftIds = new Set([...existingGifts].filter((id) => !ids.includes(id)))
          showWarning('planner:pages.plannerMD.gifts.unaffordableWarning', {
            floor: floorNumber,
            gifts: names.join(', '),
          })
        }
      }

      updateFloorSelection(floorIndex, {
        themePackId: ThemePackIdSchema.parse(packId),
        difficulty,
        giftIds: newGiftIds,
      })
    }
  }

  const handleGiftSelectionChange = (giftIds: Set<string>) => {
    if (setSelectedGiftIdsOverride) {
      setSelectedGiftIdsOverride(giftIds)
    } else if (updateFloorSelection && selection) {
      updateFloorSelection(floorIndex, {
        ...selection,
        giftIds,
      })
    }
  }

  const selectedThemePackId = selection?.themePackId ?? null
  const selectedDifficulty = selection?.difficulty ?? null
  const selectedGiftIds = selection?.giftIds ?? new Set<string>()

  // Hints explain why an editable surface is locked, so they only apply when the
  // section itself is editable.
  const showThemePackLockHint = !readOnly && !canSelectThemePack
  const showGiftLockHint = !readOnly && !selectedThemePackId

  const isThemePackReadOnly = readOnly || showThemePackLockHint
  const isGiftReadOnly = readOnly || showGiftLockHint

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
  const difficultyLabel =
    selectedDifficulty !== null
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

  return (
    <PlannerSection
      title={t('pages.plannerMD.floor', { number: floorNumber })}
      {...(onViewNotes !== undefined && { onViewNotes })}
    >
      <div
        className={cn(
          'flex flex-col landscape:flex-row sm:flex-row items-center landscape:items-start sm:items-start gap-4',
          className,
        )}
      >
        <div className="flex flex-col w-56 h-104 items-center landscape:shrink-0 sm:shrink-0">
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
                {showThemePackLockHint && (
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
              {showGiftLockHint && (
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
