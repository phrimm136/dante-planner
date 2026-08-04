import { Fragment, Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { DeckBuilderSummary } from '../deckBuilder/DeckBuilderSummary'
import { StartBuffSection } from '../startBuff/StartBuffSection'
import { StartGiftSummary } from '../startGift/StartGiftSummary'
import { EGOGiftObservationSummary } from '../egoGift/EGOGiftObservationSummary'
import { SkillReplacementSection } from '../skillReplacement/SkillReplacementSection'
import { ComprehensiveGiftGridTracker } from './ComprehensiveGiftGridTracker'
import { FloorGalleryTracker } from './FloorGalleryTracker'
import { PlannerSection } from '../PlannerSection'
import { NoteEditor } from '@/shared/noteEditor/components/NoteEditor'
import { Skeleton } from '@/components/ui/skeleton'
import { useProgressiveReveal } from '@/components/hooks/useProgressiveReveal'
import type { SaveablePlanner, MDPlannerContent } from '../../types/PlannerTypes'
import { FLOOR_COUNTS } from '@/shared/gameData'
import type { MDCategory } from '@/shared/gameData'
import type { NoteContent } from '@/shared/noteEditor'
import { isNoteEmpty } from '@/shared/noteEditor'
import { deserializeSets } from '../../schemas/PlannerSchemas'
import { NOTE_SECTIONS } from './viewerSections'

import type { ReactNode } from 'react'
import { staggerDelay } from '@/lib/stagger'
import { STAGGER_STEP_MS, SECTION_STYLES } from '@/lib/constants'

/** Note-bearing sections plus the trailing floor gallery, which has none. */
const SECTION_COUNT = NOTE_SECTIONS.length + 1

interface GuideModeViewerProps {
  planner: SaveablePlanner
}

/**
 * Read-only viewer for planner in guide mode.
 * Displays all sections from editor without editing capabilities.
 * Section order matches the editor exactly.
 */
export function GuideModeViewer({ planner }: GuideModeViewerProps) {
  const { t } = useTranslation(['planner', 'common'])
  const visibleSections = useProgressiveReveal(SECTION_COUNT)

  const content = planner.content as MDPlannerContent
  const category = planner.config.type === 'MIRROR_DUNGEON' ? planner.config.category : '5F'
  const floorCount = FLOOR_COUNTS[category as MDCategory]

  const deserialized = deserializeSets({
    selectedKeywords: content.selectedKeywords,
    selectedBuffIds: content.selectedBuffIds,
    selectedGiftIds: content.selectedGiftIds,
    observationGiftIds: content.observationGiftIds,
    comprehensiveGiftIds: content.comprehensiveGiftIds,
    floorSelections: content.floorSelections,
  })

  const readOnlyNote = (note: NoteContent | undefined) => {
    if (!note || isNoteEmpty(note)) return null

    return (
      <NoteEditor
        value={note}
        placeholder={t('pages.plannerMD.noteEditor.placeholder')}
        readOnly={true}
      />
    )
  }

  /** Each note-bearing section's body, in `NOTE_SECTIONS` order. */
  const bodies: ReactNode[] = [
    <Suspense
      key="deckBuilder"
      fallback={
        <div className="space-y-2">
          <div className="border-2 border-border rounded-lg p-4">
            <div className={SECTION_STYLES.LAYOUT.wrap}>
              {Array.from({ length: 12 }).map((_, i) => (
                <Skeleton key={i} className="w-16 h-20 rounded-md" style={staggerDelay(i)} />
              ))}
            </div>
          </div>
        </div>
      }
    >
      <DeckBuilderSummary
        equipment={content.equipment}
        deploymentOrder={content.deploymentOrder}
        readOnly={true}
      />
    </Suspense>,

    <Suspense
      key="startBuffs"
      fallback={
        <div className="space-y-2">
          <Skeleton className="h-32 w-full rounded-lg" />
        </div>
      }
    >
      <StartBuffSection
        mdVersion={planner.metadata.contentVersion}
        selectedBuffIds={deserialized.selectedBuffIds}
        readOnly={true}
      />
    </Suspense>,

    <Suspense
      key="startGifts"
      fallback={
        <div className="space-y-2">
          <Skeleton className="h-32 w-full rounded-lg" />
        </div>
      }
    >
      <StartGiftSummary
        selectedKeyword={content.selectedGiftKeyword}
        selectedGiftIds={deserialized.selectedGiftIds}
        readOnly={true}
      />
    </Suspense>,

    <Suspense
      key="observation"
      fallback={
        <PlannerSection title={t('pages.plannerMD.egoGiftObservation')}>
          <div className="space-y-4">
            <div className="flex justify-end">
              <div className={SECTION_STYLES.LAYOUT.rowTight}>
                <Skeleton className="w-8 h-8 rounded-md" />
                <Skeleton className="w-12 h-6" />
              </div>
            </div>
            <div className="flex flex-wrap gap-2 p-2 min-h-28">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton
                  key={i}
                  className="w-24 h-24 rounded-md"
                  style={staggerDelay(i, STAGGER_STEP_MS.LOOSE)}
                />
              ))}
            </div>
          </div>
        </PlannerSection>
      }
    >
      <EGOGiftObservationSummary
        mdVersion={planner.metadata.contentVersion}
        selectedGiftIds={deserialized.observationGiftIds}
        readOnly={true}
      />
    </Suspense>,

    <Suspense
      key="skillReplacement"
      fallback={
        <PlannerSection title={t('pages.plannerMD.skillReplacement.title')}>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
            {Array.from({ length: 12 }).map((_, i) => (
              <div
                key={i}
                className="flex flex-col items-center gap-1 p-2 rounded-lg border-2 border-border bg-card"
                style={staggerDelay(i, STAGGER_STEP_MS.NORMAL)}
              >
                <Skeleton className="w-24 h-24 rounded-md" />
                <div className="flex gap-1">
                  <Skeleton className="w-7 h-7 rounded-sm" />
                  <Skeleton className="w-7 h-7 rounded-sm" />
                  <Skeleton className="w-7 h-7 rounded-sm" />
                </div>
              </div>
            ))}
          </div>
        </PlannerSection>
      }
    >
      <SkillReplacementSection
        equipment={content.equipment}
        plannedEAState={content.skillEAState}
        readOnly={true}
      />
    </Suspense>,

    <PlannerSection
      key="comprehensiveGifts"
      title={t('pages.plannerMD.comprehensiveEgoGiftListView')}
    >
      <Suspense
        fallback={
          <div className="space-y-2">
            <Skeleton className="w-full rounded-md md:h-[178px] lg:h-[416px]" />
          </div>
        }
      >
        <ComprehensiveGiftGridTracker
          floorSelections={content.floorSelections}
          comprehensiveGiftIds={content.comprehensiveGiftIds}
          hoveredThemePackId={null}
          readOnly
        />
      </Suspense>
    </PlannerSection>,
  ]

  return (
    <div className="bg-background rounded-lg space-y-2">
      {/* Intro */}
      {!isNoteEmpty(content.sectionNotes?.intro) && (
        <PlannerSection title={t('pages.plannerMD.introduction')}>
          {readOnlyNote(content.sectionNotes.intro)}
        </PlannerSection>
      )}

      {NOTE_SECTIONS.map((section, index) => {
        if (!visibleSections[index]) return null

        return (
          <Fragment key={section.id}>
            {bodies[index]}
            {readOnlyNote(content.sectionNotes[section.noteKey])}
          </Fragment>
        )
      })}

      {/* Floor Theme Gallery */}
      {visibleSections[NOTE_SECTIONS.length] && (
        <Suspense
          fallback={
            <div className="text-center text-gray-500 py-8">
              {t('pages.plannerMD.loading.themePackData')}
            </div>
          }
        >
          <FloorGalleryTracker
            floorSelections={content.floorSelections}
            sectionNotes={content.sectionNotes}
            floorCount={floorCount}
          />
        </Suspense>
      )}

      {/* Outro */}
      {!isNoteEmpty(content.sectionNotes?.outro) && (
        <PlannerSection title={t('pages.plannerMD.closingNotes')}>
          {readOnlyNote(content.sectionNotes.outro)}
        </PlannerSection>
      )}
    </div>
  )
}
