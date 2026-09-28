import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { FloorThemeGiftSection } from '../floorTheme/FloorThemeGiftSection'
import { ReadOnlyNote } from '@/shared/noteEditor/components/ReadOnlyNote'
import type { SerializableFloorSelection } from '../../types/PlannerTypes'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { NoteContent } from '@/shared/noteEditor'
import { isNoteEmpty } from '@/shared/noteEditor'
import type { MDCategory } from '@/shared/gameData'
import { toFloorThemeSelection } from '../../lib/editorStateCodec'

interface FloorGalleryTrackerProps {
  floorSelections: SerializableFloorSelection[]
  sectionNotes: Record<string, NoteContent>
  floorCount: number
  category: MDCategory
}

export function FloorGalleryTracker({
  floorSelections,
  sectionNotes,
  floorCount,
  category,
}: FloorGalleryTrackerProps) {
  const { t } = useTranslation(['planner', 'common'])

  const floorIndices = Array.from({ length: floorCount }, (_, i) => i)

  const deserializedFloorSelections: FloorThemeSelection[] = floorSelections.map(
    (floor, floorIndex) => toFloorThemeSelection(floor, category, floorIndex),
  )

  return (
    <PlannerSection title={t('pages.plannerMD.floorThemes')}>
      <div className="space-y-4">
        {floorIndices.map((floorIndex) => {
          const floorNumber = floorIndex + 1
          const floorNoteKey = `floor-${floorIndex}`
          const floorNote = sectionNotes[floorNoteKey]
          return (
            <div key={floorIndex} className="space-y-2">
              <FloorThemeGiftSection
                floorNumber={floorNumber}
                floorIndex={floorIndex}
                floorSelectionsOverride={deserializedFloorSelections}
                readOnly={true}
              />
              {floorNote && !isNoteEmpty(floorNote) && <ReadOnlyNote value={floorNote} />}
            </div>
          )
        })}
      </div>
    </PlannerSection>
  )
}
