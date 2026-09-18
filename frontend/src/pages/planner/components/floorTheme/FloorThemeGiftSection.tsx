import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useShallow } from 'zustand/shallow'
import { useThemePackListSpec, useThemePackListI18n } from '@/pages/themePack'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/pages/egoGift'
import { showWarning } from '@/lib/errorPresentation'
import { usePlannerEditorStoreSafe } from '../../stores/usePlannerEditorStore'
import { DifficultyIndicator, getFloorDifficultyLabel } from './DifficultyIndicator'
import { ThemePackViewer, ThemePackPlaceholder } from './ThemePackViewer'
import { ThemePackSelectorPane } from './ThemePackSelectorPane'
import { FloorGiftViewer } from './FloorGiftViewer'
import { FloorGiftSelectorPane } from './FloorGiftSelectorPane'
import {
  DUNGEON_IDX,
  type DungeonIdx,
  type EncodedGiftId,
  type MDCategory,
} from '@/shared/gameData'
import { cn } from '@/lib/utils'
import { canSelectFloorThemePack, getUnaffordableGiftNames } from '../../lib/plannerRules'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import type { FloorThemeSelection } from '@/pages/themePack'
import { ThemePackIdSchema } from '@/shared/gameData'

const EMPTY_PACK_IDS: string[] = []

interface FloorThemeGiftSectionProps {
  floorNumber: number
  floorIndex: number
  category?: MDCategory
  readOnly?: boolean
  className?: string
  onViewNotes?: () => void
  floorSelectionsOverride?: FloorThemeSelection[]
  onThemePackSelectOverride?: (packId: string, difficulty: DungeonIdx) => void
  setSelectedGiftIdsOverride?: (giftIds: Set<EncodedGiftId>) => void
}

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
  const themePackList = useThemePackListSpec()
  const themePackI18n = useThemePackListI18n()
  const egoGiftSpec = useEGOGiftListSpec()
  const egoGiftI18n = useEGOGiftListI18n()

  const [isThemePackPaneOpen, setIsThemePackPaneOpen] = useState(false)
  const [isGiftPaneOpen, setIsGiftPaneOpen] = useState(false)

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

  const handleThemePackSelect = (packId: string, difficulty: DungeonIdx) => {
    if (onThemePackSelectOverride) {
      onThemePackSelectOverride(packId, difficulty)
    } else if (updateFloorSelection) {
      const existingGifts = selection?.giftIds ?? new Set<EncodedGiftId>()

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

  const handleGiftSelectionChange = (giftIds: Set<EncodedGiftId>) => {
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
  const selectedGiftIds = selection?.giftIds ?? new Set<EncodedGiftId>()

  const showThemePackLockHint = !readOnly && !canSelectThemePack
  const showGiftLockHint = !readOnly && !selectedThemePackId

  const isThemePackReadOnly = readOnly || showThemePackLockHint
  const isGiftReadOnly = readOnly || showGiftLockHint

  const selectedPackEntry = selectedThemePackId ? themePackList[selectedThemePackId] : null
  const selectedPackI18n = selectedThemePackId ? themePackI18n[selectedThemePackId] : null
  const selectedPackName = selectedPackI18n?.name ?? null

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
          'flex flex-col items-stretch w-fit mx-auto gap-4',
          'landscape:flex-row landscape:w-auto sm:flex-row sm:w-auto',
          className,
        )}
      >
        <div className="flex flex-col items-center landscape:shrink-0 sm:shrink-0">
          <DifficultyIndicator difficulty={difficultyLabel} />

          <div className="shrink-0">
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger asChild>
                  {selectedThemePackId && selectedPackEntry && selectedPackName ? (
                    <ThemePackViewer
                      packId={selectedThemePackId}
                      packEntry={selectedPackEntry}
                      packName={selectedPackName}
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

        <div className="flex-1 landscape:mt-6 sm:mt-6 min-w-0">
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
