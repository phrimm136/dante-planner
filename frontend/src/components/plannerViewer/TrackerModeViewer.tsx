import { Suspense, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { PlannerSection } from '@/components/common/PlannerSection'
import { SectionNoteDialog } from '@/components/common/SectionNoteDialog'
import { StartBuffSection } from '@/components/startBuff/StartBuffSection'
import { StartGiftSummary } from '@/components/startGift/StartGiftSummary'
import { EGOGiftObservationSummary } from '@/components/egoGift/EGOGiftObservationSummary'
import { Skeleton } from '@/components/ui/skeleton'
import { DeckTrackerPanel } from './DeckTrackerPanel'
import { DeckBuilderPane } from '@/components/deckBuilder/DeckBuilderPane'
import { SkillReplacementSection } from '@/components/skillReplacement/SkillReplacementSection'
import { ComprehensiveGiftGridTracker } from './ComprehensiveGiftGridTracker'
import { HorizontalThemePackGallery } from './HorizontalThemePackGallery'
import { useTrackerState } from '@/hooks/useTrackerState'
import { useProgressiveReveal } from '@/hooks/useProgressiveReveal'
import { useIdentityListSpec } from '@/hooks/useIdentityListData'
import { useEGOListSpec } from '@/hooks/useEGOListData'
import { DEFAULT_SKILL_EA } from '@/lib/constants'
import { cn } from '@/lib/utils'
import { encodeDeckCode, decodeDeckCode, validateDeckCode, type DecodedDeck } from '@/lib/deckCode'
import { deserializeSets } from '@/schemas/PlannerSchemas'
import type { SaveablePlanner, MDPlannerContent } from '@/types/PlannerTypes'

const SECTION_COUNT = 6

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
  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [pendingImport, setPendingImport] = useState<DecodedDeck | null>(null)

  const content = planner.content as MDPlannerContent

  // Section note dialog states
  const [deckBuilderNotesOpen, setDeckBuilderNotesOpen] = useState(false)
  const [startBuffsNotesOpen, setStartBuffsNotesOpen] = useState(false)
  const [startGiftsNotesOpen, setStartGiftsNotesOpen] = useState(false)
  const [observationNotesOpen, setObservationNotesOpen] = useState(false)
  const [skillReplacementNotesOpen, setSkillReplacementNotesOpen] = useState(false)
  const [comprehensiveGiftsNotesOpen, setComprehensiveGiftsNotesOpen] = useState(false)
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

  const deserialized = useMemo(
    () =>
      deserializeSets({
        selectedKeywords: content.selectedKeywords,
        selectedBuffIds: content.selectedBuffIds,
        selectedGiftIds: content.selectedGiftIds,
        observationGiftIds: content.observationGiftIds,
        comprehensiveGiftIds: content.comprehensiveGiftIds,
        floorSelections: content.floorSelections,
      }),
    [content]
  )

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
      {/* Section 0: Deck Builder - Equipment read-only, deployment editable */}
      <div className={cn('transition-opacity duration-200', visibleSections[0] ? 'opacity-100' : 'opacity-0')}>
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
            onViewNotes={() => setDeckBuilderNotesOpen(true)}
          />
        </Suspense>
      </div>

      {/* Deck Edit Pane - Uses override props for tracker mode */}
      <DeckBuilderPane
        open={deckEditPaneOpen}
        onOpenChange={setDeckEditPaneOpen}
        onImport={handleDeckImport}
        onExport={handleDeckExport}
        onResetOrder={() => setDeploymentOrder([])}
        equipmentOverride={trackerState.equipment}
        deploymentOrderOverride={trackerState.deploymentOrder}
        setEquipmentOverride={setEquipment}
        setDeploymentOrderOverride={setDeploymentOrder}
        onIdentityChange={(sinnerCode) => {
          setCurrentSkillCounts((prev) => ({
            ...prev,
            [sinnerCode]: { ...DEFAULT_SKILL_EA },
          }))
        }}
      />

      {/* Section 1: Start Buff - Read-only */}
      <div className={cn('transition-opacity duration-200', visibleSections[1] ? 'opacity-100' : 'opacity-0')}>
        <Suspense
          fallback={
            <div className="space-y-2">
              <Skeleton className="h-32 w-full rounded-lg" />
            </div>
          }
        >
          <StartBuffSection
            mdVersion={planner.metadata.contentVersion as 5 | 6}
            selectedBuffIdsOverride={deserialized.selectedBuffIds}
            onClick={() => {}}
            readOnly={true}
            onViewNotes={() => setStartBuffsNotesOpen(true)}
          />
        </Suspense>
      </div>

      {/* Section 2: Start Gift - Read-only */}
      <div className={cn('transition-opacity duration-200', visibleSections[2] ? 'opacity-100' : 'opacity-0')}>
        <Suspense
          fallback={
            <div className="space-y-2">
              <Skeleton className="h-32 w-full rounded-lg" />
            </div>
          }
        >
          <StartGiftSummary
            selectedKeywordOverride={content.selectedGiftKeyword}
            selectedGiftIdsOverride={deserialized.selectedGiftIds}
            onClick={() => {}}
            readOnly={true}
            onViewNotes={() => setStartGiftsNotesOpen(true)}
          />
        </Suspense>
      </div>

      {/* Section 3: EGO Gift Observation - Read-only */}
      <div className={cn('transition-opacity duration-200', visibleSections[3] ? 'opacity-100' : 'opacity-0')}>
        <Suspense
          fallback={
            <PlannerSection title={t('pages.plannerMD.egoGiftObservation')}>
              <div className="space-y-4">
                <div className="flex flex-wrap gap-2 p-2 min-h-28">
                  {Array.from({ length: 6 }).map((_, i) => (
                    <Skeleton key={i} className="w-24 h-24 rounded-md" style={{ animationDelay: `${i * 80}ms` }} />
                  ))}
                </div>
              </div>
            </PlannerSection>
          }
        >
          <EGOGiftObservationSummary selectedGiftIdsOverride={deserialized.observationGiftIds} onClick={() => {}} readOnly={true} onViewNotes={() => setObservationNotesOpen(true)} />
        </Suspense>
      </div>

      {/* Section 4: Skill Replacement - Current skill counts editable */}
      <div className={cn('transition-opacity duration-200', visibleSections[4] ? 'opacity-100' : 'opacity-0')}>
        <Suspense
          fallback={
            <PlannerSection title={t('pages.plannerMD.skillReplacement.title')}>
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
                {Array.from({ length: 12 }).map((_, i) => (
                  <Skeleton key={i} className="h-32 rounded-lg" style={{ animationDelay: `${i * 60}ms` }} />
                ))}
              </div>
            </PlannerSection>
          }
        >
          <SkillReplacementSection
            equipmentOverride={trackerState.equipment}
            plannedEAStateOverride={content.skillEAState}
            currentEAState={trackerState.currentSkillCounts}
            setSkillEAStateOverride={setCurrentSkillCounts}
            onViewNotes={() => setSkillReplacementNotesOpen(true)}
          />
        </Suspense>
      </div>

      {/* Section 5: EGO Gift List and Theme Pack Collection - Side by side */}
      <div className={cn('transition-opacity duration-200', visibleSections[5] ? 'opacity-100' : 'opacity-0')}>
        <div className="flex flex-col md:flex-row gap-2">
          {/* Comprehensive Gifts from all floors */}
          <div className="md:w-1/2 md:min-w-0">
            <PlannerSection title={t('pages.plannerMD.comprehensiveEgoGiftListView')} onViewNotes={() => setComprehensiveGiftsNotesOpen(true)}>
              <Suspense
                fallback={
                  <div className="text-center text-gray-500 py-8">{t('pages.plannerMD.loading.EGOGiftData')}</div>
                }
              >
                <ComprehensiveGiftGridTracker
                  floorSelections={content.floorSelections}
                  hoveredThemePackId={hoveredThemePackId}
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
                <div className="text-center text-gray-500 py-8">{t('pages.plannerMD.loading.themePackData')}</div>
              }
            >
              <HorizontalThemePackGallery
                floorSelections={content.floorSelections}
                sectionNotes={content.sectionNotes}
                doneMarks={trackerState.doneMarks}
                onTogglePackDone={togglePackDone}
                onHoverChange={setHoveredThemePackId}
              />
            </Suspense>
          </div>
        </div>
      </div>

      {/* Section Note Dialogs */}
      <SectionNoteDialog
        open={deckBuilderNotesOpen}
        onOpenChange={setDeckBuilderNotesOpen}
        sectionTitle={t('pages.plannerMD.deckBuilder')}
        noteContent={content.sectionNotes.deckBuilder}
        readOnly={true}
      />
      <SectionNoteDialog
        open={startBuffsNotesOpen}
        onOpenChange={setStartBuffsNotesOpen}
        sectionTitle={t('pages.plannerMD.startBuffs')}
        noteContent={content.sectionNotes.startBuffs}
        readOnly={true}
      />
      <SectionNoteDialog
        open={startGiftsNotesOpen}
        onOpenChange={setStartGiftsNotesOpen}
        sectionTitle={t('pages.plannerMD.startEgoGift')}
        noteContent={content.sectionNotes.startGifts}
        readOnly={true}
      />
      <SectionNoteDialog
        open={observationNotesOpen}
        onOpenChange={setObservationNotesOpen}
        sectionTitle={t('pages.plannerMD.egoGiftObservation')}
        noteContent={content.sectionNotes.observation}
        readOnly={true}
      />
      <SectionNoteDialog
        open={skillReplacementNotesOpen}
        onOpenChange={setSkillReplacementNotesOpen}
        sectionTitle={t('pages.plannerMD.skillReplacement.title')}
        noteContent={content.sectionNotes.skillReplacement}
        readOnly={true}
      />
      <SectionNoteDialog
        open={comprehensiveGiftsNotesOpen}
        onOpenChange={setComprehensiveGiftsNotesOpen}
        sectionTitle={t('pages.plannerMD.comprehensiveEgoGiftListView')}
        noteContent={content.sectionNotes.comprehensiveGifts}
        readOnly={true}
      />

      {/* Deck Import Confirmation Dialog */}
      <Dialog open={importDialogOpen} onOpenChange={setImportDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('deckBuilder.importConfirmTitle')}</DialogTitle>
            <DialogDescription>
              {t('deckBuilder.importConfirmDescription')}
            </DialogDescription>
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
            <Button onClick={handleImportConfirm}>
              {t('deckBuilder.apply')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

    </div>
  )
}
