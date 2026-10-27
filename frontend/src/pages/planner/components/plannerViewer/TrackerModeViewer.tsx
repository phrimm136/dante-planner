import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { RevealSection } from '../RevealSection'
import type { RevealSectionSpec } from '../RevealSection'
import {
  DeckGridSkeleton,
  GiftGridSkeleton,
  GiftGridTrackerSkeleton,
  SkillGridSkeleton,
  StartBuffSkeleton,
  StartGiftSkeleton,
} from '../plannerSkeletons'
import { SectionNoteDialog } from '../SectionNoteDialog'
import { StartBuffSection } from '../startBuff/StartBuffSection'
import { StartGiftSummary } from '../startGift/StartGiftSummary'
import { EGOGiftObservationSummary } from '../egoGift/EGOGiftObservationSummary'
import { DeckBuilderBlock } from '../deckBuilder/DeckBuilderBlock'
import { usePlannerStore } from '../../stores/usePlannerStore'
import { SkillReplacementSection } from '../skillReplacement/SkillReplacementSection'
import { ComprehensiveGiftGridTracker } from './ComprehensiveGiftGridTracker'
import { ReadOnlyNote } from '@/shared/noteEditor/components/ReadOnlyNote'
import { HorizontalThemePackGallery } from './HorizontalThemePackGallery'
import { useTrackerDoneMarks } from '../../hooks/useTrackerDoneMarks'
import { useProgressiveReveal } from '@/components/hooks/useProgressiveReveal'
import { floorCount as categoryFloorCount } from '@/shared/gameData'
import { isNoteEmpty } from '@/shared/noteEditor'
import { NOTE_SECTIONS } from './viewerSections'
import type { NoteSectionId } from './viewerSections'
import { deserializeSets } from '../../schemas/PlannerSchemas'
import type { MDSaveablePlanner } from '../../types/PlannerTypes'

const SECTION_COUNT = NOTE_SECTIONS.length

interface TrackerModeViewerProps {
  planner: MDSaveablePlanner
}

export function TrackerModeViewer({ planner }: TrackerModeViewerProps) {
  const { t } = useTranslation(['planner', 'common'])
  const visibleSections = useProgressiveReveal(SECTION_COUNT)
  const [hoveredThemePackId, setHoveredThemePackId] = useState<string | null>(null)
  const [focusedThemePackId, setFocusedThemePackId] = useState<string | null>(null)
  const activeThemePackId = hoveredThemePackId ?? focusedThemePackId

  const { content } = planner
  const floorCount = categoryFloorCount(planner.config.category)
  const introNote = content.sectionNotes?.intro
  const outroNote = content.sectionNotes?.outro

  const [openNote, setOpenNote] = useState<NoteSectionId | null>(null)

  const equipment = usePlannerStore((s) => s.equipment)
  const setEquipment = usePlannerStore((s) => s.setEquipment)
  const setDeploymentOrder = usePlannerStore((s) => s.setDeploymentOrder)
  const skillEAState = usePlannerStore((s) => s.skillEAState)
  const updateSinnerSkillEA = usePlannerStore((s) => s.updateSinnerSkillEA)
  const { doneMarks, egoGiftDoneMarks, toggleEgoGiftDoneMark, togglePackDone } =
    useTrackerDoneMarks()

  const deserialized = deserializeSets(
    {
      selectedKeywords: content.selectedKeywords,
      selectedBuffIds: content.selectedBuffIds,
      selectedGiftIds: content.selectedGiftIds,
      observationGiftIds: content.observationGiftIds,
      comprehensiveGiftIds: content.comprehensiveGiftIds,
      floorSelections: content.floorSelections,
    },
    planner.config.category,
  )

  const handleResetToPreset = () => {
    setDeploymentOrder(content.deploymentOrder)
    setEquipment(content.equipment)
  }

  const sections: RevealSectionSpec[] = [
    {
      id: 'deckBuilder',
      node: (
        <Suspense fallback={<DeckGridSkeleton />}>
          <DeckBuilderBlock
            tracker={{
              onResetToInitial: handleResetToPreset,
              onViewNotes: () => setOpenNote('deckBuilder'),
            }}
          />
        </Suspense>
      ),
    },

    {
      id: 'startBuffs',
      node: (
        <Suspense fallback={<StartBuffSkeleton />}>
          <StartBuffSection
            selectedBuffIds={deserialized.selectedBuffIds}
            readOnly={true}
            onViewNotes={() => setOpenNote('startBuffs')}
          />
        </Suspense>
      ),
    },

    {
      id: 'startGifts',
      node: (
        <Suspense fallback={<StartGiftSkeleton />}>
          <StartGiftSummary
            selectedKeyword={content.selectedGiftKeyword}
            selectedGiftIds={deserialized.selectedGiftIds}
            readOnly={true}
            onViewNotes={() => setOpenNote('startGifts')}
          />
        </Suspense>
      ),
    },

    {
      id: 'observation',
      node: (
        <Suspense fallback={<GiftGridSkeleton title={t('pages.plannerMD.egoGiftObservation')} />}>
          <EGOGiftObservationSummary
            selectedGiftIds={deserialized.observationGiftIds}
            readOnly={true}
            onViewNotes={() => setOpenNote('observation')}
          />
        </Suspense>
      ),
    },

    {
      id: 'skillReplacement',
      node: (
        <Suspense
          fallback={<SkillGridSkeleton title={t('pages.plannerMD.skillReplacement.title')} />}
        >
          <SkillReplacementSection
            equipment={equipment}
            plannedEAState={content.skillEAState}
            currentEAState={skillEAState}
            onSinnerSkillEAChange={updateSinnerSkillEA}
            onViewNotes={() => setOpenNote('skillReplacement')}
          />
        </Suspense>
      ),
    },

    {
      id: 'comprehensiveGifts',
      node: (
        <div className="flex flex-col md:flex-row md:items-stretch gap-2">
          <div className="md:w-1/2 md:min-w-0">
            <PlannerSection
              title={t('pages.plannerMD.comprehensiveEgoGiftListView')}
              onViewNotes={() => setOpenNote('comprehensiveGifts')}
              fill
            >
              <Suspense fallback={<GiftGridTrackerSkeleton />}>
                <ComprehensiveGiftGridTracker
                  floorSelections={content.floorSelections}
                  floorCount={floorCount}
                  comprehensiveGiftIds={content.comprehensiveGiftIds}
                  hoveredThemePackId={activeThemePackId}
                  egoGiftDoneMarks={egoGiftDoneMarks}
                  onToggleEgoGiftDone={toggleEgoGiftDoneMark}
                />
              </Suspense>
            </PlannerSection>
          </div>

          <div className="md:w-1/2 md:min-w-0 overflow-hidden">
            <Suspense
              fallback={
                <div className="text-center text-gray-500 py-8">
                  {t('pages.plannerMD.loading.themePackData')}
                </div>
              }
            >
              <HorizontalThemePackGallery
                floorSelections={content.floorSelections}
                floorCount={floorCount}
                sectionNotes={content.sectionNotes}
                doneMarks={doneMarks}
                onTogglePackDone={togglePackDone}
                focusedThemePackId={focusedThemePackId}
                onFocusToggle={(packId) =>
                  setFocusedThemePackId((prev) => (prev === packId ? null : packId))
                }
                onHoverChange={setHoveredThemePackId}
              />
            </Suspense>
          </div>
        </div>
      ),
    },
  ]

  return (
    <div className="bg-background rounded-lg space-y-2">
      {introNote && !isNoteEmpty(introNote) && (
        <PlannerSection title={t('pages.plannerMD.introduction')}>
          <ReadOnlyNote value={introNote} />
        </PlannerSection>
      )}

      {sections.map((section, index) => (
        <RevealSection key={section.id} mode="fade" visible={visibleSections[index] ?? false}>
          {section.node}
        </RevealSection>
      ))}

      {outroNote && !isNoteEmpty(outroNote) && (
        <PlannerSection title={t('pages.plannerMD.closingNotes')}>
          <ReadOnlyNote value={outroNote} />
        </PlannerSection>
      )}

      {NOTE_SECTIONS.map((section) => (
        <SectionNoteDialog
          key={section.id}
          open={openNote === section.id}
          onOpenChange={(next) => setOpenNote(next ? section.id : null)}
          sectionTitle={t(section.titleKey)}
          noteContent={content.sectionNotes[section.noteKey]}
        />
      ))}
    </div>
  )
}
