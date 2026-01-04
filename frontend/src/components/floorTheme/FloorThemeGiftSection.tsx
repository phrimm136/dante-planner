import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useThemePackListData } from '@/hooks/useThemePackListData'
import { DifficultyIndicator, getFloorDifficultyLabel } from './DifficultyIndicator'
import { ThemePackViewer, ThemePackPlaceholder } from './ThemePackViewer'
import { ThemePackSelectorPane } from './ThemePackSelectorPane'
import { FloorGiftViewer } from './FloorGiftViewer'
import { FloorGiftSelectorPane } from './FloorGiftSelectorPane'
import { DUNGEON_IDX, type DungeonIdx } from '@/lib/constants'
import { cn } from '@/lib/utils'
import { PlannerSection } from '@/components/common/PlannerSection'

interface FloorThemeGiftSectionProps {
  floorNumber: number // 1-indexed (1-15)
  previousFloorDifficulty: DungeonIdx | null
  selectedThemePackId: string | null
  selectedDifficulty: DungeonIdx | null
  selectedGiftIds: Set<string>
  onThemePackSelect: (packId: string, difficulty: DungeonIdx) => void
  setSelectedGiftIds: (giftIds: Set<string>) => void
  className?: string
}

/**
 * Container for a single floor's theme pack and gift selection
 * Layout: Floor label | Difficulty indicator | Theme pack viewer | Gift viewer
 */
export function FloorThemeGiftSection({
  floorNumber,
  previousFloorDifficulty,
  selectedThemePackId,
  selectedDifficulty,
  selectedGiftIds,
  onThemePackSelect,
  setSelectedGiftIds,
  className,
}: FloorThemeGiftSectionProps) {
  const { t } = useTranslation()
  const { themePackList, themePackI18n } = useThemePackListData()

  const [isThemePackPaneOpen, setIsThemePackPaneOpen] = useState(false)
  const [isGiftPaneOpen, setIsGiftPaneOpen] = useState(false)

  // Get the selected theme pack entry and name
  const selectedPackEntry = selectedThemePackId ? themePackList[selectedThemePackId] : null
  const selectedPackName = selectedThemePackId ? themePackI18n[selectedThemePackId]?.name : null

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

  return (
    <PlannerSection title={t('pages.plannerMD.floor', { number: floorNumber })}>
      <div className={cn('flex items-start gap-4', className)}>
        <div className="flex flex-col w-56">
          {/* Difficulty indicator */}
          <DifficultyIndicator difficulty={difficultyLabel} />

          {/* Theme pack viewer */}
          <div className="shrink-0">
            {selectedThemePackId && selectedPackEntry && selectedPackName ? (
              <ThemePackViewer
                packId={selectedThemePackId}
                packEntry={selectedPackEntry}
                packName={selectedPackName}
                onClick={handleOpenThemePackPane}
              />
            ) : (
              <ThemePackPlaceholder onClick={handleOpenThemePackPane} />
            )}
          </div>
        </div>

        {/* Gift viewer */}
        <div className="flex-1 mt-5 min-w-0">
          <FloorGiftViewer
            selectedGiftIds={selectedGiftIds}
            onClick={handleOpenGiftPane}
          />
        </div>

        {/* Theme pack selector pane */}
        <ThemePackSelectorPane
          open={isThemePackPaneOpen}
          onOpenChange={setIsThemePackPaneOpen}
          floorNumber={floorNumber}
          previousFloorDifficulty={previousFloorDifficulty}
          themePackList={themePackList}
          themePackI18n={themePackI18n}
          onSelect={onThemePackSelect}
        />

        {/* Gift selector pane */}
        {selectedThemePackId && (
          <FloorGiftSelectorPane
            open={isGiftPaneOpen}
            onOpenChange={setIsGiftPaneOpen}
            floorNumber={floorNumber}
            themePackId={selectedThemePackId}
            selectedGiftIds={selectedGiftIds}
            setSelectedGiftIds={setSelectedGiftIds}
          />
        )}
      </div>
    </PlannerSection>
  )
}
