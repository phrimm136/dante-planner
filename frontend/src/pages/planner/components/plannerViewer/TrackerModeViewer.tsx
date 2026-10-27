import { Fragment, Suspense, useState } from 'react'
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
import { DeckTrackerPanel } from './DeckTrackerPanel'
import { DeckBuilderPane } from '../deckBuilder/DeckBuilderPane'
import { TrackerDeckBuilderContent } from '../deckBuilder/DeckBuilderContent'
import { collectOwnedGiftIds } from '../../lib/deckEA'
import { DeckImportDialog } from '../deckBuilder/DeckImportDialog'
import { SkillReplacementSection } from '../skillReplacement/SkillReplacementSection'
import { ComprehensiveGiftGridTracker } from './ComprehensiveGiftGridTracker'
import { ReadOnlyNote } from '@/shared/noteEditor/components/ReadOnlyNote'
import { HorizontalThemePackGallery } from './HorizontalThemePackGallery'
import { useTrackerState } from '../../hooks/useTrackerState'
import { useDeckClipboard } from '../../hooks/useDeckClipboard'
import { useIdentityMaxLevel } from '../../hooks/useMirrorDungeonSeason'
import { useProgressiveReveal } from '@/components/hooks/useProgressiveReveal'
import { DEFAULT_SKILL_EA, floorCount as categoryFloorCount } from '@/shared/gameData'
import { isNoteEmpty } from '@/shared/noteEditor'
import { NOTE_SECTIONS } from './viewerSections'
import type { NoteSectionId } from './viewerSections'
import { deserializeSets } from '../../schemas/PlannerSchemas'
import type { MDSaveablePlanner } from '../../types/PlannerTypes'
import type { DecodedDeck } from '../../lib/deckCode'
import type { TrackerDeckBuilderContentProps } from '../deckBuilder/DeckBuilderContent'

const SECTION_COUNT = NOTE_SECTIONS.length

function SeasonTrackerDeckBuilderContent({
  mdVersion,
  ...props
}: Omit<TrackerDeckBuilderContentProps, 'maxLevel'> & { mdVersion: number }) {
  const maxLevel = useIdentityMaxLevel(mdVersion)
  return <TrackerDeckBuilderContent {...props} maxLevel={maxLevel} />
}

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
  const [deckEditPaneOpen, setDeckEditPaneOpen] = useState(false)

  const {
    state: trackerState,
    setEquipment,
    setDeploymentOrder,
    setCurrentSkillCounts,
    toggleEgoGiftDoneMark,
    togglePackDone,
  } = useTrackerState(content.equipment, content.deploymentOrder)

  const {
    handleExport: handleDeckExport,
    openImport,
    importDraft,
    setImportText,
    closeImport,
  } = useDeckClipboard({
    readDeck: () => ({
      equipment: trackerState.equipment,
      deploymentOrder: trackerState.deploymentOrder,
    }),
    mdVersion: planner.metadata.contentVersion,
  })

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

  const handleDeckImport = () => {
    void openImport()
  }

  const handleImportApply = (deck: DecodedDeck) => {
    setEquipment(deck.equipment)
    setDeploymentOrder(deck.deploymentOrder)

    closeImport()
  }

  const handleResetToPreset = () => {
    setDeploymentOrder(content.deploymentOrder)
    setEquipment(content.equipment)
  }

  const sections: RevealSectionSpec[] = [
    {
      id: 'deckBuilder',
      node: (
        <Suspense fallback={<DeckGridSkeleton />}>
          <DeckTrackerPanel
            equipment={trackerState.equipment}
            deploymentOrder={trackerState.deploymentOrder}
            ownedGiftIds={collectOwnedGiftIds(deserialized, floorCount)}
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
      aside: (
        <DeckBuilderPane open={deckEditPaneOpen} onOpenChange={setDeckEditPaneOpen}>
          <SeasonTrackerDeckBuilderContent
            mdVersion={planner.metadata.contentVersion}
            isActive={deckEditPaneOpen}
            ownedGiftIds={collectOwnedGiftIds(deserialized, floorCount)}
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
        <Suspense fallback={<StartBuffSkeleton />}>
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
            mdVersion={planner.metadata.contentVersion}
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
                  egoGiftDoneMarks={trackerState.egoGiftDoneMarks}
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
      {introNote && !isNoteEmpty(introNote) && (
        <PlannerSection title={t('pages.plannerMD.introduction')}>
          <ReadOnlyNote value={introNote} />
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

      <DeckImportDialog
        draft={importDraft}
        onTextChange={setImportText}
        onApply={handleImportApply}
        onClose={closeImport}
      />
    </div>
  )
}
