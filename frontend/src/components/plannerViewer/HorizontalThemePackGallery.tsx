import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { ScrollArea, ScrollBar } from '@/components/ui/scroll-area'
import { PlannerSection } from '@/components/common/PlannerSection'
import { ThemePackTrackerCard } from './ThemePackTrackerCard'
import { useThemePackListData } from '@/hooks/useThemePackListData'
import type { SerializableFloorSelection } from '@/types/PlannerTypes'
import type { NoteContent } from '@/types/NoteEditorTypes'

interface HorizontalThemePackGalleryProps {
  floorSelections: SerializableFloorSelection[]
  sectionNotes: Record<string, NoteContent>
  doneMarks: Record<number, Set<string>>
  onToggleDone: (floorIndex: number, themePackId: string) => void
  floorCount: number
  onHoverChange: (themePackId: string | null) => void
}

/**
 * Horizontal scrolling gallery of theme pack cards for tracker mode (separate PlannerSection)
 * Shows all theme packs from all floors in single unified collection
 */
export function HorizontalThemePackGallery({
  floorSelections,
  sectionNotes,
  doneMarks,
  onToggleDone,
  floorCount,
  onHoverChange,
}: HorizontalThemePackGalleryProps) {
  const { t } = useTranslation(['planner', 'common'])
  const { spec, i18n } = useThemePackListData()

  // Collect selected theme pack IDs from all floors
  const allThemePackIds = useMemo(() => {
    const packIds: string[] = []
    floorSelections.forEach((selection) => {
      if (selection.themePackId) {
        packIds.push(selection.themePackId)
      }
    })
    return packIds
  }, [floorSelections])

  // Get all done marks across all floors
  const allDoneMarks = useMemo(() => {
    const marks = new Set<string>()
    Object.values(doneMarks).forEach((floorMarks) => {
      floorMarks.forEach((packId) => marks.add(packId))
    })
    return marks
  }, [doneMarks])

  // Find floor index for a given theme pack from floorSelections
  const getFloorIndexForPack = (themePackId: string): number => {
    return floorSelections.findIndex((sel) => sel.themePackId === themePackId)
  }

  // Get note content for a theme pack based on its floor
  const getNoteContentForPack = (themePackId: string): NoteContent => {
    const floorIndex = getFloorIndexForPack(themePackId)
    const floorNoteKey = `floor-${floorIndex}`
    return sectionNotes[floorNoteKey] || { type: 'doc', content: [] }
  }

  return (
    <PlannerSection title={t('pages.plannerMD.floorThemes')}>
      <ScrollArea className="w-full whitespace-nowrap">
        <div className="flex gap-4 p-2 pb-4">
          {allThemePackIds.map((packId) => {
            const packEntry = spec[packId]
            const packName = i18n[packId]?.name || packId
            if (!packEntry) return null

            const floorIndex = getFloorIndexForPack(packId)

            return (
              <ThemePackTrackerCard
                key={packId}
                packId={packId}
                packEntry={packEntry}
                packName={packName}
                floorNumber={floorIndex + 1}
                noteContent={getNoteContentForPack(packId)}
                isDone={allDoneMarks.has(packId)}
                onToggleDone={() => {
                  onToggleDone(floorIndex, packId)
                }}
                onHoverChange={(hovering) => onHoverChange(hovering ? packId : null)}
              />
            )
          })}
        </div>
        <ScrollBar orientation="horizontal" />
      </ScrollArea>
    </PlannerSection>
  )
}
