import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/common/PlannerSection'
import { FloorThemeGiftSection } from '@/components/floorTheme/FloorThemeGiftSection'
import { NoteEditor } from '@/components/noteEditor/NoteEditor'
import type { SerializableFloorSelection } from '@/types/PlannerTypes'
import type { FloorThemeSelection } from '@/types/ThemePackTypes'
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

  // Deserialize floor selections (convert giftIds from string[] to Set<string>)
  const deserializedFloorSelections = useMemo<FloorThemeSelection[]>(() =>
    floorSelections.map(floor => ({
      ...floor,
      giftIds: new Set(floor.giftIds)
    })),
    [floorSelections]
  )

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
