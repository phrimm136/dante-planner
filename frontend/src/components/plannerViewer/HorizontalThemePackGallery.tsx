import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CheckCircle2, FileText } from 'lucide-react'
import { ScrollArea, ScrollBar } from '@/components/ui/scroll-area'
import { Button } from '@/components/ui/button'
import { PlannerSection } from '@/components/common/PlannerSection'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
import { ThemePackViewer } from '@/components/floorTheme/ThemePackViewer'
import { FloorNoteDialog } from './FloorNoteDialog'
import { useThemePackListData } from '@/hooks/useThemePackListData'
import { CARD_GRID } from '@/lib/constants'
import { cn, getDisplayFontForLanguage } from '@/lib/utils'
import type { SerializableFloorSelection } from '@/types/PlannerTypes'
import type { NoteContent } from '@/types/NoteEditorTypes'

interface HorizontalThemePackGalleryProps {
  floorSelections: SerializableFloorSelection[]
  sectionNotes: Record<string, NoteContent>
  doneMarks: Record<number, Set<string>>
  onToggleDone: (floorIndex: number, themePackId: string) => void
  onHoverChange: (themePackId: string | null) => void
}

/**
 * Horizontal scrolling gallery of theme pack cards for tracker mode
 * Shows all theme packs from all floors in single unified collection
 */
export function HorizontalThemePackGallery({
  floorSelections,
  sectionNotes,
  doneMarks,
  onToggleDone,
  onHoverChange,
}: HorizontalThemePackGalleryProps) {
  const { t, i18n: i18nInstance } = useTranslation(['planner', 'common'])
  const { spec, i18n } = useThemePackListData()
  const [hoveredPackId, setHoveredPackId] = useState<string | null>(null)
  const [notesDialogPack, setNotesDialogPack] = useState<{
    packId: string
    packName: string
    floorNumber: number
    noteContent: NoteContent
  } | null>(null)

  const mobileScale = CARD_GRID.MOBILE_SCALE.DENSE

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

  const handleMouseEnter = (packId: string) => {
    setHoveredPackId(packId)
    onHoverChange(packId)
  }

  const handleMouseLeave = () => {
    setHoveredPackId(null)
    onHoverChange(null)
  }

  return (
    <>
      <PlannerSection title={t('pages.plannerMD.floorThemes')}>
        <ScrollArea className="md:h-[306px] lg:h-[481px] whitespace-nowrap">
          <div className="flex gap-4 p-2 pb-4">
            {allThemePackIds.map((packId) => {
              const packEntry = spec[packId]
              const i18nData = i18n[packId]
              const packName = i18nData?.name || packId
              if (!packEntry) return null

              const floorIndex = getFloorIndexForPack(packId)
              const isDone = allDoneMarks.has(packId)
              const isHovered = hoveredPackId === packId

              return (
                <div
                  key={packId}
                  className="flex flex-col items-center flex-shrink-0"
                  onMouseEnter={() => handleMouseEnter(packId)}
                  onMouseLeave={handleMouseLeave}
                >
                  <span
                    className="text-lg mb-1"
                    style={getDisplayFontForLanguage(i18nInstance.language)}
                  >
                    {t('pages.plannerMD.floor', { number: floorIndex + 1 })}
                  </span>
                  <ScaledCardWrapper
                    mobileScale={mobileScale}
                    cardWidth={CARD_GRID.WIDTH.THEME_PACK}
                    cardHeight={CARD_GRID.HEIGHT.THEME_PACK}
                  >
                    <ThemePackViewer
                      packId={packId}
                      packEntry={packEntry}
                      packName={packName}
                      specialName={i18nData?.specialName}
                      enableHoverHighlight
                      readOnly
                      className={cn(isDone && 'brightness-50')}
                      overlay={
                        isHovered && (
                          <div className="absolute inset-0 flex items-center justify-center gap-4">
                            <Button
                              size="icon"
                              variant={isDone ? 'default' : 'secondary'}
                              onClick={(e) => {
                                e.stopPropagation()
                                onToggleDone(floorIndex, packId)
                              }}
                              aria-label={isDone ? t('common.markAsNotDone', 'Mark as Not Done') : t('common.markAsDone', 'Mark as Done')}
                            >
                              <CheckCircle2 className="h-5 w-5" />
                            </Button>
                            <Button
                              size="icon"
                              variant="secondary"
                              onClick={(e) => {
                                e.stopPropagation()
                                setNotesDialogPack({
                                  packId,
                                  packName,
                                  floorNumber: floorIndex + 1,
                                  noteContent: getNoteContentForPack(packId),
                                })
                              }}
                              aria-label={t('common.viewNotes', 'View Notes')}
                            >
                              <FileText className="h-5 w-5" />
                            </Button>
                          </div>
                        )
                      }
                    />
                  </ScaledCardWrapper>
                </div>
              )
            })}
          </div>
          <ScrollBar orientation="horizontal" />
        </ScrollArea>
      </PlannerSection>

      {/* Notes Dialog */}
      {notesDialogPack && (
        <FloorNoteDialog
          open={true}
          onOpenChange={(open) => !open && setNotesDialogPack(null)}
          floorNumber={notesDialogPack.floorNumber}
          themePackName={notesDialogPack.packName}
          noteContent={notesDialogPack.noteContent}
        />
      )}
    </>
  )
}
