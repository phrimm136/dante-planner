import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { DeckBuilderSummary } from '../deckBuilder/DeckBuilderSummary'
import { StartBuffSection } from '../startBuff/StartBuffSection'
import { StartGiftSummary } from '../startGift/StartGiftSummary'
import { EGOGiftObservationSummary } from '../egoGift/EGOGiftObservationSummary'
import { SkillReplacementSection } from '../skillReplacement/SkillReplacementSection'
import { ComprehensiveGiftGridTracker } from './ComprehensiveGiftGridTracker'
import { FloorGalleryTracker } from './FloorGalleryTracker'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { RevealSection } from '../RevealSection'
import type { RevealSectionSpec } from '../RevealSection'
import {
  DeckGridSkeleton,
  GiftGridSkeleton,
  SectionBlockSkeleton,
  SkillGridSkeleton,
} from '../plannerSkeletons'
import { NoteEditor } from '@/shared/noteEditor/components/NoteEditor'
import { useProgressiveReveal } from '@/components/hooks/useProgressiveReveal'
import type { MDSaveablePlanner } from '../../types/PlannerTypes'
import { FLOOR_COUNTS } from '@/shared/gameData'
import type { NoteContent } from '@/shared/noteEditor'
import { isNoteEmpty } from '@/shared/noteEditor'
import { deserializeSets } from '../../schemas/PlannerSchemas'
import { NOTE_SECTIONS } from './viewerSections'

/** Note-bearing sections plus the trailing floor gallery, which has none. */
const SECTION_COUNT = NOTE_SECTIONS.length + 1

interface GuideModeViewerProps {
  planner: MDSaveablePlanner
}

/**
 * Read-only viewer for planner in guide mode.
 * Displays all sections from editor without editing capabilities.
 * Section order matches the editor exactly.
 */
export function GuideModeViewer({ planner }: GuideModeViewerProps) {
  const { t } = useTranslation(['planner', 'common'])
  const visibleSections = useProgressiveReveal(SECTION_COUNT)

  const { content } = planner
  const category = planner.config.category
  const floorCount = FLOOR_COUNTS[category]

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

  const sections: RevealSectionSpec[] = [
    {
      id: 'deckBuilder',
      node: (
        <>
          <Suspense fallback={<DeckGridSkeleton />}>
            <DeckBuilderSummary
              equipment={content.equipment}
              deploymentOrder={content.deploymentOrder}
              readOnly={true}
            />
          </Suspense>
          {readOnlyNote(content.sectionNotes.deckBuilder)}
        </>
      ),
    },

    {
      id: 'startBuffs',
      node: (
        <>
          <Suspense fallback={<SectionBlockSkeleton />}>
            <StartBuffSection
              mdVersion={planner.metadata.contentVersion}
              selectedBuffIds={deserialized.selectedBuffIds}
              readOnly={true}
            />
          </Suspense>
          {readOnlyNote(content.sectionNotes.startBuffs)}
        </>
      ),
    },

    {
      id: 'startGifts',
      node: (
        <>
          <Suspense fallback={<SectionBlockSkeleton />}>
            <StartGiftSummary
              selectedKeyword={content.selectedGiftKeyword}
              selectedGiftIds={deserialized.selectedGiftIds}
              readOnly={true}
            />
          </Suspense>
          {readOnlyNote(content.sectionNotes.startGifts)}
        </>
      ),
    },

    {
      id: 'observation',
      node: (
        <>
          <Suspense
            fallback={
              <GiftGridSkeleton title={t('pages.plannerMD.egoGiftObservation')} showCount />
            }
          >
            <EGOGiftObservationSummary
              mdVersion={planner.metadata.contentVersion}
              selectedGiftIds={deserialized.observationGiftIds}
              readOnly={true}
            />
          </Suspense>
          {readOnlyNote(content.sectionNotes.observation)}
        </>
      ),
    },

    {
      id: 'skillReplacement',
      node: (
        <>
          <Suspense
            fallback={<SkillGridSkeleton title={t('pages.plannerMD.skillReplacement.title')} />}
          >
            <SkillReplacementSection
              equipment={content.equipment}
              plannedEAState={content.skillEAState}
              readOnly={true}
            />
          </Suspense>
          {readOnlyNote(content.sectionNotes.skillReplacement)}
        </>
      ),
    },

    {
      id: 'comprehensiveGifts',
      node: (
        <>
          <PlannerSection title={t('pages.plannerMD.comprehensiveEgoGiftListView')}>
            <Suspense
              fallback={
                <SectionBlockSkeleton className="w-full rounded-md md:h-[178px] lg:h-[416px]" />
              }
            >
              <ComprehensiveGiftGridTracker
                floorSelections={content.floorSelections}
                comprehensiveGiftIds={content.comprehensiveGiftIds}
                hoveredThemePackId={null}
                readOnly
              />
            </Suspense>
          </PlannerSection>
          {readOnlyNote(content.sectionNotes.comprehensiveGifts)}
        </>
      ),
    },

    {
      id: 'floorGallery',
      node: (
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
      ),
    },
  ]

  return (
    <div className="bg-background rounded-lg space-y-2">
      {/* Intro */}
      {!isNoteEmpty(content.sectionNotes?.intro) && (
        <PlannerSection title={t('pages.plannerMD.introduction')}>
          {readOnlyNote(content.sectionNotes.intro)}
        </PlannerSection>
      )}

      {sections.map((section, index) => (
        <RevealSection key={section.id} visible={visibleSections[index] ?? false}>
          {section.node}
        </RevealSection>
      ))}

      {/* Outro */}
      {!isNoteEmpty(content.sectionNotes?.outro) && (
        <PlannerSection title={t('pages.plannerMD.closingNotes')}>
          {readOnlyNote(content.sectionNotes.outro)}
        </PlannerSection>
      )}
    </div>
  )
}
