import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from '@/lib/toast'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { PlannerSection } from '../PlannerSection'
import { SectionNoteDialog } from '../SectionNoteDialog'
import { StartBuffSection } from '../startBuff/StartBuffSection'
import { StartGiftSummary } from '../startGift/StartGiftSummary'
import { EGOGiftObservationSummary } from '../egoGift/EGOGiftObservationSummary'
import { Skeleton } from '@/components/ui/skeleton'
import { DeckTrackerPanel } from './DeckTrackerPanel'
import { DeckBuilderPane } from '../deckBuilder/DeckBuilderPane'
import { TrackerDeckBuilderContent } from '../deckBuilder/DeckBuilderContent'
import { SkillReplacementSection } from '../skillReplacement/SkillReplacementSection'
import { ComprehensiveGiftGridTracker } from './ComprehensiveGiftGridTracker'
import { NoteEditor } from '@/shared/noteEditor/components/NoteEditor'
import { HorizontalThemePackGallery } from './HorizontalThemePackGallery'
import { useTrackerState } from '../../hooks/useTrackerState'
import { useProgressiveReveal } from '@/components/hooks/useProgressiveReveal'
import { useIdentityListSpec } from '@/pages/identity'
import { useEGOListSpec } from '@/pages/ego'
import { DEFAULT_SKILL_EA } from '@/shared/gameData'
import { isNoteEmpty } from '@/shared/noteEditor'
import { cn } from '@/lib/utils'
import { NOTE_SECTIONS } from './viewerSections'
import type { NoteSectionId } from './viewerSections'
import {
  encodeDeckCode,
  decodeDeckCode,
  validateDeckCode,
  type DecodedDeck,
} from '../../lib/deckCode'
import { deserializeSets } from '../../schemas/PlannerSchemas'
import type { SaveablePlanner, MDPlannerContent } from '../../types/PlannerTypes'
import { staggerDelay } from '@/lib/stagger'
import { STAGGER_STEP_MS } from '@/lib/constants'

const SECTION_COUNT = NOTE_SECTIONS.length

interface TrackerModeViewerProps {
  planner: SaveablePlanner
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
  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [pendingImport, setPendingImport] = useState<DecodedDeck | null>(null)

  const content = planner.content as MDPlannerContent

  // At most one section note dialog is open at a time
  const [openNote, setOpenNote] = useState<NoteSectionId | null>(null)
  const [deckEditPaneOpen, setDeckEditPaneOpen] = useState(false)

  // Load identity and EGO spec for deck code
  const identitySpec = useIdentityListSpec()
  const egoSpec = useEGOListSpec()

  // Initialize tracker state (session-only, resets on unmount/refresh)
  const {
    state: trackerState,
    setEquipment,
    setDeploymentOrder,
    setCurrentSkillCounts,
    toggleEgoGiftDoneMark,
    togglePackDone,
  } = useTrackerState(content.equipment, content.deploymentOrder)

  const deserialized = deserializeSets({
    selectedKeywords: content.selectedKeywords,
    selectedBuffIds: content.selectedBuffIds,
    selectedGiftIds: content.selectedGiftIds,
    observationGiftIds: content.observationGiftIds,
    comprehensiveGiftIds: content.comprehensiveGiftIds,
    floorSelections: content.floorSelections,
  })

  const handleDeckExport = async () => {
    try {
      const code = encodeDeckCode(trackerState.equipment, trackerState.deploymentOrder)
      await navigator.clipboard.writeText(code)
      toast.success(t('deckBuilder.exportSuccess'))
    } catch {
      toast.error(t('deckBuilder.exportError'))
    }
  }

  const handleDeckImport = async () => {
    try {
      const clipboardText = await navigator.clipboard.readText()
      const validation = validateDeckCode(clipboardText)

      if (!validation.isValid) {
        toast.error(t('deckBuilder.importError'))
        return
      }

      const decoded = decodeDeckCode(clipboardText, identitySpec, egoSpec)
      setPendingImport(decoded)
      setImportDialogOpen(true)
    } catch {
      toast.error(t('deckBuilder.importError'))
    }
  }

  const handleImportConfirm = () => {
    if (!pendingImport) return

    setEquipment(pendingImport.equipment)
    setDeploymentOrder(pendingImport.deploymentOrder)

    setImportDialogOpen(false)
    setPendingImport(null)
  }

  const handleResetToPreset = () => {
    // Reset deployment order and equipment to planner's preset
    setDeploymentOrder(content.deploymentOrder)
    setEquipment(content.equipment)
  }

  return (
    <div className="bg-background rounded-lg space-y-2">
      {/* Intro */}
      {!isNoteEmpty(content.sectionNotes?.intro) && (
        <PlannerSection title={t('pages.plannerMD.introduction')}>
          <NoteEditor
            value={content.sectionNotes.intro}
            placeholder={t('pages.plannerMD.noteEditor.placeholder')}
            readOnly={true}
          />
        </PlannerSection>
      )}

      {/* Section 0: Deck Builder - Equipment read-only, deployment editable */}
      <div
        className={cn(
          'transition-opacity duration-200',
          visibleSections[0] ? 'opacity-100' : 'opacity-0',
        )}
      >
        <Suspense
          fallback={
            <div className="space-y-2">
              <Skeleton className="h-64 w-full rounded-lg" />
            </div>
          }
        >
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
      </div>

      {/* Deck Edit Pane - session-only deck, not the planner editor store */}
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

      {/* Section 1: Start Buff - Read-only */}
      <div
        className={cn(
          'transition-opacity duration-200',
          visibleSections[1] ? 'opacity-100' : 'opacity-0',
        )}
      >
        <Suspense
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
            onViewNotes={() => setOpenNote('startBuffs')}
          />
        </Suspense>
      </div>

      {/* Section 2: Start Gift - Read-only */}
      <div
        className={cn(
          'transition-opacity duration-200',
          visibleSections[2] ? 'opacity-100' : 'opacity-0',
        )}
      >
        <Suspense
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
            onViewNotes={() => setOpenNote('startGifts')}
          />
        </Suspense>
      </div>

      {/* Section 3: EGO Gift Observation - Read-only */}
      <div
        className={cn(
          'transition-opacity duration-200',
          visibleSections[3] ? 'opacity-100' : 'opacity-0',
        )}
      >
        <Suspense
          fallback={
            <PlannerSection title={t('pages.plannerMD.egoGiftObservation')}>
              <div className="space-y-4">
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
            onViewNotes={() => setOpenNote('observation')}
          />
        </Suspense>
      </div>

      {/* Section 4: Skill Replacement - Current skill counts editable */}
      <div
        className={cn(
          'transition-opacity duration-200',
          visibleSections[4] ? 'opacity-100' : 'opacity-0',
        )}
      >
        <Suspense
          fallback={
            <PlannerSection title={t('pages.plannerMD.skillReplacement.title')}>
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
                {Array.from({ length: 12 }).map((_, i) => (
                  <Skeleton
                    key={i}
                    className="h-32 rounded-lg"
                    style={staggerDelay(i, STAGGER_STEP_MS.NORMAL)}
                  />
                ))}
              </div>
            </PlannerSection>
          }
        >
          <SkillReplacementSection
            equipment={trackerState.equipment}
            plannedEAState={content.skillEAState}
            currentEAState={trackerState.currentSkillCounts}
            setSkillEAState={setCurrentSkillCounts}
            onViewNotes={() => setOpenNote('skillReplacement')}
          />
        </Suspense>
      </div>

      {/* Section 5: EGO Gift List and Theme Pack Collection - Side by side */}
      <div
        className={cn(
          'transition-opacity duration-200',
          visibleSections[5] ? 'opacity-100' : 'opacity-0',
        )}
      >
        <div className="flex flex-col md:flex-row gap-2">
          {/* Comprehensive Gifts from all floors */}
          <div className="md:w-1/2 md:min-w-0">
            <PlannerSection
              title={t('pages.plannerMD.comprehensiveEgoGiftListView')}
              onViewNotes={() => setOpenNote('comprehensiveGifts')}
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
      </div>

      {/* Outro */}
      {!isNoteEmpty(content.sectionNotes?.outro) && (
        <PlannerSection title={t('pages.plannerMD.closingNotes')}>
          <NoteEditor
            value={content.sectionNotes.outro}
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

      {/* Deck Import Confirmation Dialog */}
      <Dialog open={importDialogOpen} onOpenChange={setImportDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('deckBuilder.importConfirmTitle')}</DialogTitle>
            <DialogDescription>{t('deckBuilder.importConfirmDescription')}</DialogDescription>
          </DialogHeader>

          {pendingImport && pendingImport.warnings.length > 0 && (
            <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-md p-3">
              <p className="text-sm font-medium text-yellow-800 dark:text-yellow-200 mb-2">
                {t('deckBuilder.importWarnings')}
              </p>
              <ul className="text-sm text-yellow-700 dark:text-yellow-300 list-disc list-inside">
                {pendingImport.warnings.map((warning, index) => (
                  <li key={index}>{warning}</li>
                ))}
              </ul>
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setImportDialogOpen(false)}>
              {t('deckBuilder.cancel')}
            </Button>
            <Button onClick={handleImportConfirm}>{t('deckBuilder.apply')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
