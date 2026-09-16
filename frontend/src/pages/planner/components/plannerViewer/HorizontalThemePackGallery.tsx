import { useTranslation } from 'react-i18next'
import { ScrollArea, ScrollBar } from '@/components/ui/scroll-area'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { ThemePackTrackerCard } from './ThemePackTrackerCard'
import { useThemePackListSpec, useThemePackListI18n } from '@/pages/themePack'
import { CARD_MOBILE_SCALE_DENSE } from '@/lib/constants'
import { getDisplayFontForLanguage } from '@/lib/utils'
import { EmptyStatePlaceholder } from '@/components/feedback/EmptyStatePlaceholder'
import { createEmptyNoteContent } from '@/shared/noteEditor'
import type { SerializableFloorSelection } from '../../types/PlannerTypes'
import type { NoteContent } from '@/shared/noteEditor'

const EMPTY_NOTE = createEmptyNoteContent()

interface HorizontalThemePackGalleryProps {
  floorSelections: SerializableFloorSelection[]
  sectionNotes: Record<string, NoteContent>
  doneMarks: Record<number, Set<string>>
  onTogglePackDone: (floorIndex: number, themePackId: string, giftIds: string[]) => void
  focusedThemePackId: string | null
  onFocusToggle: (themePackId: string) => void
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
  focusedThemePackId,
  onFocusToggle,
  onHoverChange,
}: HorizontalThemePackGalleryProps) {
  const { t, i18n: i18nInstance } = useTranslation(['planner', 'common'])
  const spec = useThemePackListSpec()
  const i18n = useThemePackListI18n()

  const mobileScale = CARD_MOBILE_SCALE_DENSE

  // Collect selected theme pack IDs from all floors
  const allThemePackIds = (() => {
    const packIds: string[] = []
    floorSelections.forEach((selection) => {
      if (selection.themePackId) {
        packIds.push(selection.themePackId)
      }
    })
    return packIds
  })()

  // Get all done marks across all floors
  const allDoneMarks = (() => {
    const marks = new Set<string>()
    Object.values(doneMarks).forEach((floorMarks) => {
      floorMarks.forEach((packId) => marks.add(packId))
    })
    return marks
  })()

  // Find floor index for a given theme pack from floorSelections
  const getFloorIndexForPack = (themePackId: string): number => {
    return floorSelections.findIndex((sel) => sel.themePackId === themePackId)
  }

  // Get note content for a theme pack based on its floor
  const getNoteContentForPack = (themePackId: string): NoteContent => {
    const floorIndex = getFloorIndexForPack(themePackId)
    const floorNoteKey = `floor-${floorIndex}`
    return sectionNotes[floorNoteKey] ?? EMPTY_NOTE
  }

  // No theme packs selected at all
  if (allThemePackIds.length === 0) {
    return (
      <PlannerSection title={t('pages.plannerMD.floorThemes')} fill>
        <EmptyStatePlaceholder
          label={t('pages.plannerMD.emptyState.noThemePack')}
          className="flex-1 min-h-0"
        />
      </PlannerSection>
    )
  }

  return (
    <PlannerSection title={t('pages.plannerMD.floorThemes')} fill>
      <ScrollArea className="flex-1 min-h-0 whitespace-nowrap">
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
              <div key={packId} className="flex flex-col items-center flex-shrink-0">
                <span
                  className="text-lg mb-1"
                  style={getDisplayFontForLanguage(i18nInstance.language)}
                >
                  {t('pages.plannerMD.floor', { number: floorIndex + 1 })}
                </span>
                <ThemePackTrackerCard
                  packId={packId}
                  packEntry={packEntry}
                  packName={packName}
                  floorNumber={floorIndex + 1}
                  noteContent={getNoteContentForPack(packId)}
                  isDone={isDone}
                  isFocused={focusedThemePackId === packId}
                  mobileScale={mobileScale}
                  onFocusToggle={() => onFocusToggle(packId)}
                  onToggleDone={() => onTogglePackDone(floorIndex, packId, giftIds)}
                  onHoverChange={(hovering) => onHoverChange(hovering ? packId : null)}
                />
              </div>
            )
          })}
        </div>
        <ScrollBar orientation="horizontal" />
      </ScrollArea>
    </PlannerSection>
  )
}
