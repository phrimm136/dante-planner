import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/common/PlannerSection'
import { FloorThemeGiftSection } from '@/components/floorTheme/FloorThemeGiftSection'
import { NoteEditor } from '@/components/noteEditor/NoteEditor'
import type { SerializableFloorSelection } from '@/types/PlannerTypes'
import type { DungeonIdx } from '@/lib/constants'
import type { NoteContent } from '@/types/NoteEditorTypes'

interface FloorGalleryTrackerProps {
  floorSelections: SerializableFloorSelection[]
  sectionNotes: Record<string, NoteContent>
  floorCount: number
}

/**
 * Floor-by-floor gallery for guide mode (separate PlannerSection)
 * Shows theme pack and gifts for each floor without hover highlighting
 */
export function FloorGalleryTracker({
  floorSelections,
  sectionNotes,
  floorCount,
}: FloorGalleryTrackerProps) {
  const { t } = useTranslation(['planner', 'common'])

  const floorIndices = useMemo(() => Array.from({ length: floorCount }, (_, i) => i), [floorCount])

  const getPreviousFloorDifficulty = (floorIndex: number): DungeonIdx | null => {
    if (floorIndex === 0) return null
    return floorSelections[floorIndex - 1].difficulty
  }

  return (
    <PlannerSection title={t('pages.plannerMD.floorThemes')}>
      <div className="space-y-4">
        {floorIndices.map((floorIndex) => {
          const floorNumber = floorIndex + 1
          const selection = floorSelections[floorIndex]
          const floorNoteKey = `floor-${floorIndex}`
          return (
            <div key={floorIndex} className="space-y-2">
              <FloorThemeGiftSection
                floorNumber={floorNumber}
                floorIndex={floorIndex}
                floorSelections={floorSelections}
                previousFloorDifficulty={getPreviousFloorDifficulty(floorIndex)}
                selectedThemePackId={selection.themePackId}
                selectedDifficulty={selection.difficulty}
                selectedGiftIds={selection.giftIds}
                onThemePackSelect={() => {}}
                setSelectedGiftIds={() => {}}
                readOnly={true}
              />
              <NoteEditor
                value={sectionNotes[floorNoteKey]}
                onChange={() => {}}
                placeholder={t('pages.plannerMD.noteEditor.placeholder')}
                readOnly={true}
              />
            </div>
          )
        })}
      </div>
    </PlannerSection>
  )
}
