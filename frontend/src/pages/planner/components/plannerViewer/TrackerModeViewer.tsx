import { Fragment, Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { RevealSection } from '../RevealSection'
import type { RevealSectionSpec } from '../RevealSection'
import { GiftGridSkeleton, SectionBlockSkeleton, SkillGridSkeleton } from '../plannerSkeletons'
import { SectionNoteDialog } from '../SectionNoteDialog'
import { StartBuffSection } from '../startBuff/StartBuffSection'
import { StartGiftSummary } from '../startGift/StartGiftSummary'
import { EGOGiftObservationSummary } from '../egoGift/EGOGiftObservationSummary'
import { DeckTrackerPanel } from './DeckTrackerPanel'
import { DeckBuilderPane } from '../deckBuilder/DeckBuilderPane'
import { TrackerDeckBuilderContent } from '../deckBuilder/DeckBuilderContent'
import { DeckImportConfirmDialog } from '../deckBuilder/DeckImportConfirmDialog'
import { SkillReplacementSection } from '../skillReplacement/SkillReplacementSection'
import { ComprehensiveGiftGridTracker } from './ComprehensiveGiftGridTracker'
import { NoteEditor } from '@/shared/noteEditor/components/NoteEditor'
import { HorizontalThemePackGallery } from './HorizontalThemePackGallery'
import { useTrackerState } from '../../hooks/useTrackerState'
import { useDeckClipboard } from '../../hooks/useDeckClipboard'
import { useProgressiveReveal } from '@/components/hooks/useProgressiveReveal'
import { DEFAULT_SKILL_EA } from '@/shared/gameData'
import { isNoteEmpty } from '@/shared/noteEditor'
import { NOTE_SECTIONS } from './viewerSections'
import type { NoteSectionId } from './viewerSections'
import { deserializeSets } from '../../schemas/PlannerSchemas'
import type { MDSaveablePlanner } from '../../types/PlannerTypes'

const SECTION_COUNT = NOTE_SECTIONS.length

interface TrackerModeViewerProps {
  planner: MDSaveablePlanner
}

/**
 * Tracker mode viewer for planner
 *
 * Single column layout with same section order as editor
 * Selective editability: deployment order and current skill counts are editable (session-only)
 * All other sections are read-only
 */
export function TrackerModeViewer({ planner }: TrackerModeViewerProps) {
  const { t } = useTranslation(['planner', 'common'])
  const visibleSections = useProgressiveReveal(SECTION_COUNT)
  const [hoveredThemePackId, setHoveredThemePackId] = useState<string | null>(null)
  const [focusedThemePackId, setFocusedThemePackId] = useState<string | null>(null)
  const activeThemePackId = hoveredThemePackId ?? focusedThemePackId

  const { content } = planner
  const introNote = content.sectionNotes?.intro
  const outroNote = content.sectionNotes?.outro

  // At most one section note dialog is open at a time
  const [openNote, setOpenNote] = useState<NoteSectionId | null>(null)
  const [deckEditPaneOpen, setDeckEditPaneOpen] = useState(false)

  // Initialize tracker state (session-only, resets on unmount/refresh)
  const {
    state: trackerState,
    setEquipment,
    setDeploymentOrder,
    setCurrentSkillCounts,
    toggleEgoGiftDoneMark,
    togglePackDone,
  } = useTrackerState(content.equipment, content.deploymentOrder)

  const {
    handleImport: handleDeckImport,
    handleExport: handleDeckExport,
    pendingImport,
    clearPending,
  } = useDeckClipboard({
    readDeck: () => ({
      equipment: trackerState.equipment,
      deploymentOrder: trackerState.deploymentOrder,
    }),
  })

  const deserialized = deserializeSets({
    selectedKeywords: content.selectedKeywords,
    selectedBuffIds: content.selectedBuffIds,
    selectedGiftIds: content.selectedGiftIds,
    observationGiftIds: content.observationGiftIds,
    comprehensiveGiftIds: content.comprehensiveGiftIds,
    floorSelections: content.floorSelections,
  })

  const handleImportConfirm = () => {
    if (!pendingImport) return

    setEquipment(pendingImport.equipment)
    setDeploymentOrder(pendingImport.deploymentOrder)

    clearPending()
  }

  const handleResetToPreset = () => {
    // Reset deployment order and equipment to planner's preset
    setDeploymentOrder(content.deploymentOrder)
    setEquipment(content.equipment)
  }

  const sections: RevealSectionSpec[] = [
    {
      // Equipment read-only, deployment editable
      id: 'deckBuilder',
      node: (
        <Suspense fallback={<SectionBlockSkeleton className="h-64 w-full rounded-lg" />}>
          <DeckTrackerPanel
            equipment={trackerState.equipment}
            deploymentOrder={trackerState.deploymentOrder}
            setEquipment={setEquipment}
            setDeploymentOrder={setDeploymentOrder}
            onEditDeck={() => setDeckEditPaneOpen(true)}
            onImport={handleDeckImport}
            onExport={handleDeckExport}
            onResetToPreset={handleResetToPreset}
            onViewNotes={() => setOpenNote('deckBuilder')}
          />
        </Suspense>
      ),
      // Session-only deck, not the planner editor store
      aside: (
        <DeckBuilderPane open={deckEditPaneOpen} onOpenChange={setDeckEditPaneOpen}>
          <TrackerDeckBuilderContent
            isActive={deckEditPaneOpen}
            equipment={trackerState.equipment}
            setEquipment={setEquipment}
            deploymentOrder={trackerState.deploymentOrder}
            setDeploymentOrder={setDeploymentOrder}
            onImport={handleDeckImport}
            onExport={handleDeckExport}
            onResetOrder={() => setDeploymentOrder([])}
            onIdentityChange={(sinnerCode) => {
              setCurrentSkillCounts((prev) => ({
                ...prev,
                [sinnerCode]: { ...DEFAULT_SKILL_EA },
              }))
            }}
          />
        </DeckBuilderPane>
      ),
    },

    {
      id: 'startBuffs',
      node: (
        <Suspense fallback={<SectionBlockSkeleton />}>
          <StartBuffSection
            mdVersion={planner.metadata.contentVersion}
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
        <Suspense fallback={<SectionBlockSkeleton />}>
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
            mdVersion={planner.metadata.contentVersion}
            selectedGiftIds={deserialized.observationGiftIds}
            readOnly={true}
            onViewNotes={() => setOpenNote('observation')}
          />
        </Suspense>
      ),
    },

    {
      // Current skill counts editable
      id: 'skillReplacement',
      node: (
        <Suspense
          fallback={<SkillGridSkeleton title={t('pages.plannerMD.skillReplacement.title')} />}
        >
          <SkillReplacementSection
            equipment={trackerState.equipment}
            plannedEAState={content.skillEAState}
            currentEAState={trackerState.currentSkillCounts}
            setSkillEAState={setCurrentSkillCounts}
            onViewNotes={() => setOpenNote('skillReplacement')}
          />
        </Suspense>
      ),
    },

    {
      // EGO gift list and theme pack collection, side by side
      id: 'comprehensiveGifts',
      node: (
        <div className="flex flex-col md:flex-row gap-2">
          {/* Comprehensive Gifts from all floors */}
          <div className="md:w-1/2 md:min-w-0">
            <PlannerSection
              title={t('pages.plannerMD.comprehensiveEgoGiftListView')}
              onViewNotes={() => setOpenNote('comprehensiveGifts')}
            >
              <Suspense
                fallback={
                  <SectionBlockSkeleton className="w-full rounded-md md:h-[178px] lg:h-[416px]" />
                }
              >
                <ComprehensiveGiftGridTracker
                  floorSelections={content.floorSelections}
                  comprehensiveGiftIds={content.comprehensiveGiftIds}
                  hoveredThemePackId={activeThemePackId}
                  egoGiftDoneMarks={trackerState.egoGiftDoneMarks}
                  onToggleEgoGiftDone={toggleEgoGiftDoneMark}
                />
              </Suspense>
            </PlannerSection>
          </div>

          {/* Theme Pack Gallery - All floors in single horizontal scroll */}
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
                sectionNotes={content.sectionNotes}
                doneMarks={trackerState.doneMarks}
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
      {/* Intro */}
      {introNote && !isNoteEmpty(introNote) && (
        <PlannerSection title={t('pages.plannerMD.introduction')}>
          <NoteEditor
            value={introNote}
            placeholder={t('pages.plannerMD.noteEditor.placeholder')}
            readOnly={true}
          />
        </PlannerSection>
      )}

      {sections.map((section, index) => (
        <Fragment key={section.id}>
          <RevealSection mode="fade" visible={visibleSections[index] ?? false}>
            {section.node}
          </RevealSection>
          {section.aside}
        </Fragment>
      ))}

      {/* Outro */}
      {outroNote && !isNoteEmpty(outroNote) && (
        <PlannerSection title={t('pages.plannerMD.closingNotes')}>
          <NoteEditor
            value={outroNote}
            placeholder={t('pages.plannerMD.noteEditor.placeholder')}
            readOnly={true}
          />
        </PlannerSection>
      )}

      {/* Section Note Dialogs */}
      {NOTE_SECTIONS.map((section) => (
        <SectionNoteDialog
          key={section.id}
          open={openNote === section.id}
          onOpenChange={(next) => setOpenNote(next ? section.id : null)}
          sectionTitle={t(section.titleKey)}
          noteContent={content.sectionNotes[section.noteKey]}
          readOnly={true}
        />
      ))}

      <DeckImportConfirmDialog
        pendingImport={pendingImport}
        onConfirm={handleImportConfirm}
        onCancel={clearPending}
      />
    </div>
  )
}
