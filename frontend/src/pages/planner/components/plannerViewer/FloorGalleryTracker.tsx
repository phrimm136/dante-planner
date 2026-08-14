import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { FloorThemeGiftSection } from '../floorTheme/FloorThemeGiftSection'
import { NoteEditor } from '@/shared/noteEditor/components/NoteEditor'
import type { SerializableFloorSelection } from '../../types/PlannerTypes'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { NoteContent } from '@/shared/noteEditor'
import { isNoteEmpty } from '@/shared/noteEditor'

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

  const floorIndices = Array.from({ length: floorCount }, (_, i) => i)

  // Deserialize floor selections (convert giftIds from string[] to Set<string>)
  const deserializedFloorSelections: FloorThemeSelection[] = floorSelections.map((floor) => ({
    ...floor,
    giftIds: new Set(floor.giftIds),
  }))

  return (
    <PlannerSection title={t('pages.plannerMD.floorThemes')}>
      <div className="space-y-4">
        {floorIndices.map((floorIndex) => {
          const floorNumber = floorIndex + 1
          const floorNoteKey = `floor-${floorIndex}`
          return (
            <div key={floorIndex} className="space-y-2">
              <FloorThemeGiftSection
                floorNumber={floorNumber}
                floorIndex={floorIndex}
                floorSelectionsOverride={deserializedFloorSelections}
                readOnly={true}
              />
              {!isNoteEmpty(sectionNotes[floorNoteKey]) && (
                <NoteEditor
                  value={sectionNotes[floorNoteKey]}
                  placeholder={t('pages.plannerMD.noteEditor.placeholder')}
                  readOnly={true}
                />
              )}
            </div>
          )
        })}
      </div>
    </PlannerSection>
  )
}
