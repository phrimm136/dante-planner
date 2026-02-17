import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { ScrollArea, ScrollBar } from '@/components/ui/scroll-area'
import { PlannerSection } from '@/components/common/PlannerSection'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
import { ThemePackTrackerCard } from './ThemePackTrackerCard'
import { useThemePackListData } from '@/hooks/useThemePackListData'
import { CARD_GRID, EMPTY_STATE } from '@/lib/constants'
import { cn, getDisplayFontForLanguage } from '@/lib/utils'
import type { SerializableFloorSelection } from '@/types/PlannerTypes'
import type { NoteContent } from '@/types/NoteEditorTypes'

interface HorizontalThemePackGalleryProps {
  floorSelections: SerializableFloorSelection[]
  sectionNotes: Record<string, NoteContent>
  doneMarks: Record<number, Set<string>>
  onTogglePackDone: (floorIndex: number, themePackId: string, giftIds: string[]) => void
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
  onTogglePackDone,
  onHoverChange,
}: HorizontalThemePackGalleryProps) {
  const { t, i18n: i18nInstance } = useTranslation(['planner', 'common'])
  const { spec, i18n } = useThemePackListData()

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

  // No theme packs selected at all
  if (allThemePackIds.length === 0) {
    return (
      <PlannerSection title={t('pages.plannerMD.floorThemes')}>
        <div
          className={cn(
            'flex items-center justify-center p-4 text-muted-foreground md:h-[306px] lg:h-[481px]',
            EMPTY_STATE.MIN_HEIGHT,
            EMPTY_STATE.DASHED_BORDER
          )}
        >
          <span className="text-sm text-center">
            {t('pages.plannerMD.emptyState.noThemePack')}
          </span>
        </div>
      </PlannerSection>
    )
  }

  return (
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
            const giftIds = floorSelections[floorIndex]?.giftIds ?? []

            return (
              <div
                key={packId}
                className="flex flex-col items-center flex-shrink-0"
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
                  <ThemePackTrackerCard
                    packId={packId}
                    packEntry={packEntry}
                    packName={packName}
                    specialName={i18nData?.specialName}
                    floorNumber={floorIndex + 1}
                    noteContent={getNoteContentForPack(packId)}
                    isDone={isDone}
                    onToggleDone={() => onTogglePackDone(floorIndex, packId, giftIds)}
                    onHoverChange={(hovering) => onHoverChange(hovering ? packId : null)}
                  />
                </ScaledCardWrapper>
              </div>
            )
          })}
        </div>
        <ScrollBar orientation="horizontal" />
      </ScrollArea>
    </PlannerSection>
  )
}
