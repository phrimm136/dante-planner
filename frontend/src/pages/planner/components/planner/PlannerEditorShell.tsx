// React core
import { useState, useEffect, useMemo, Suspense, startTransition, useRef } from 'react'

// TanStack
import { useNavigate } from '@tanstack/react-router'
import { queryClient } from '@/lib/queryClient'
import { plannerQueryKeys } from '../../lib/plannerQueryKeys'
import { publishedPlannerQueryKeys } from '../../hooks/usePublishedPlannerQuery'

// Third-party libraries
import { useTranslation } from 'react-i18next'
import { ChevronDown, Save } from 'lucide-react'

// shadcn/ui components
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

// Project utilities (@/lib)
import {
  MD_CATEGORIES,
  PLANNER_KEYWORDS,
  FLOOR_COUNTS,
  DUNGEON_IDX,
  DEFAULT_SKILL_EA,
} from '@/shared/gameData'
import { SECTION_STYLES } from '@/lib/constants'
import { getKeywordIconPath } from '@/shared/assets'
import { assertNever, calculateByteLength } from '@/lib/utils'
import { CONFLICT_TOAST_KEY } from '../../lib/conflictChoice'
import { MdCategoryLabel } from '../MdCategoryLabel'
import { showAppError, showErrorMessage, showSuccess, showWarning } from '@/lib/errorPresentation'
import { isSyncConflict } from '@/lib/apiErrorClassifier'

// Project types & schemas
import type { MDCategory } from '@/shared/gameData'
import { isMDPlanner } from '../../types/PlannerTypes'
import type { SaveablePlanner, ConflictResolutionChoice } from '../../types/PlannerTypes'

// Store
import { usePlannerEditorStore, usePlannerEditorStoreApi } from '../../stores/usePlannerEditorStore'

// Project hooks
import { useDeckClipboard } from '../../hooks/useDeckClipboard'
import { usePlannerSave } from '../../hooks/usePlannerSave'
import type { SaveOptions } from '../../hooks/usePlannerSave'
import { usePlannerConfig } from '../../hooks/usePlannerConfig'
import { useUserSettingsQuery } from '@/shared/userSettings'

// Project components (@/components)
import { StoreBoundDeckBuilderSummary } from '../deckBuilder/DeckBuilderSummary'
import { DeckBuilderPane } from '../deckBuilder/DeckBuilderPane'
import { StoreBoundDeckBuilderContent } from '../deckBuilder/DeckBuilderContent'
import { StoreBoundStartBuffSection } from '../startBuff/StartBuffSection'
import { StartBuffEditPane } from '../startBuff/StartBuffEditPane'
import { StoreBoundStartGiftSummary } from '../startGift/StartGiftSummary'
import { StartGiftEditPane } from '../startGift/StartGiftEditPane'
import { StoreBoundEGOGiftObservationSummary } from '../egoGift/EGOGiftObservationSummary'
import { EGOGiftObservationEditPane } from '../egoGift/EGOGiftObservationEditPane'
import { StoreBoundComprehensiveGiftSummary } from '../egoGift/ComprehensiveGiftSummary'
import { ComprehensiveGiftSelectorPane } from '../egoGift/ComprehensiveGiftSelectorPane'
import { StoreBoundSkillReplacementSection } from '../skillReplacement/SkillReplacementSection'
import { FloorThemeGiftSection } from '../floorTheme/FloorThemeGiftSection'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { RevealSection } from '../RevealSection'
import type { RevealSectionSpec } from '../RevealSection'
import {
  DeckGridSkeleton,
  GiftGridSkeleton,
  SectionBlockSkeleton,
  SkillGridSkeleton,
} from '../plannerSkeletons'
import { DeckImportConfirmDialog } from '../deckBuilder/DeckImportConfirmDialog'
import {
  NoteDeliveryProvider,
  useNoteDeliveryRegistry,
} from '@/shared/noteEditor/context/NoteDeliveryRegistry'
import { StoreBoundSectionNote } from './StoreBoundSectionNote'
import { ConflictResolutionDialog } from './ConflictResolutionDialog'
import { SyncOffWarningDialog } from '../SyncOffWarningDialog'
import { KeywordSelector } from './KeywordSelector'
import { LastSavedLabel } from './LastSavedLabel'

const MAX_TITLE_BYTES = 256

/**
 * The parts of an editing session the shell cannot derive for itself: which
 * planner is being edited, at which content and sync version.
 */
export interface PlannerEditorSession {
  /** Game content version the planner is authored against. */
  contentVersion: number
  /** Existing planner id; absent means the shell mints one. */
  initialPlannerId?: string | undefined
  /** Server version to present on the next sync. */
  initialSyncVersion?: number | undefined
  /** Timestamp to seed the "last saved" label with. */
  initialSavedAt?: string | undefined
}

/**
 * Every editing surface of an MD planner. Both the create and the edit route
 * render this; they differ only in the session they hand it.
 */
export function PlannerEditorShell({
  contentVersion: mdVersion,
  initialPlannerId,
  initialSyncVersion,
  initialSavedAt,
}: PlannerEditorSession) {
  const { t } = useTranslation(['planner', 'common'])

  const config = usePlannerConfig()
  const navigate = useNavigate()

  // Get user settings for sync preference
  const { data: userSettings } = useUserSettingsQuery()
  const syncEnabled = userSettings?.syncEnabled ?? false

  // Ref to skip beforeunload warning during intentional navigation (e.g., "Keep Both")
  const isIntentionalNavigationRef = useRef(false)

  // The note editors' pending text, collected on the way out.
  const noteDelivery = useNoteDeliveryRegistry()

  // Callback for "Keep Both" - navigate to the newly created copy
  const handleKeepBothCreated = (newPlannerId: string) => {
    // Mark as intentional navigation to skip "leave page?" popup
    isIntentionalNavigationRef.current = true

    // Navigate to forked planner edit page, replacing current history entry
    // Back button will go to original view (which now shows server version)
    void navigate({ to: '/planner/md/$id/edit', params: { id: newPlannerId }, replace: true })
  }

  // ============================================================================
  // Store API (for imperative access in handlers)
  // ============================================================================
  const storeApi = usePlannerEditorStoreApi()

  // ============================================================================
  // Store Subscriptions (RENDER-ONLY state - causes re-render when changed)
  // ============================================================================
  const title = usePlannerEditorStore((s) => s.title)
  const setTitle = usePlannerEditorStore((s) => s.setTitle)
  const category = usePlannerEditorStore((s) => s.category)
  const setCategory = usePlannerEditorStore((s) => s.setCategory)
  const isPublished = usePlannerEditorStore((s) => s.isPublished)
  const visibleSections = usePlannerEditorStore((s) => s.visibleSections)
  const setVisibleSections = usePlannerEditorStore((s) => s.setVisibleSections)
  const selectedKeywords = usePlannerEditorStore((s) => s.selectedKeywords)
  const setSelectedKeywords = usePlannerEditorStore((s) => s.setSelectedKeywords)

  // Actions (stable references, no re-render)
  const setEquipment = usePlannerEditorStore((s) => s.setEquipment)
  const setDeploymentOrder = usePlannerEditorStore((s) => s.setDeploymentOrder)
  const updateSinnerSkillEA = usePlannerEditorStore((s) => s.updateSinnerSkillEA)
  const initializeFromPlannerAction = usePlannerEditorStore((s) => s.initializeFromPlanner)

  // ============================================================================
  // Local useState (Dialog states - per spec)
  // ============================================================================
  const [isStartBuffPaneOpen, setIsStartBuffPaneOpen] = useState(false)
  const [isStartGiftPaneOpen, setIsStartGiftPaneOpen] = useState(false)
  const [isObservationPaneOpen, setIsObservationPaneOpen] = useState(false)
  const [isComprehensivePaneOpen, setIsComprehensivePaneOpen] = useState(false)
  const [isDeckPaneOpen, setIsDeckPaneOpen] = useState(false)
  const [showSaveWarning, setShowSaveWarning] = useState(false)

  // ============================================================================
  // Derived State
  // ============================================================================
  const floorCount = FLOOR_COUNTS[category]

  // ============================================================================
  // SSE Reload Handler - Uses store batch action
  // ============================================================================
  const handleServerReload = (reloadedPlanner: SaveablePlanner): boolean => {
    if (!isMDPlanner(reloadedPlanner)) {
      console.error('Attempted to load non-MD planner in MD editor:', reloadedPlanner.config.type)
      showErrorMessage('planner:pages.plannerMD.errors.invalidType')
      return false
    }

    initializeFromPlannerAction(reloadedPlanner.content, {
      title: reloadedPlanner.metadata.title,
      category: reloadedPlanner.config.category,
      isPublished: reloadedPlanner.metadata.published ?? false,
    })
    return true
  }

  // ============================================================================
  // Category Change Handler
  // ============================================================================
  const handleCategoryChange = (newCategory: MDCategory) => {
    const currentCategory = storeApi.getState().category
    const floorSelections = storeApi.getState().floorSelections

    // Warn if changing from 5F to 10F/15F with Normal difficulty on floors 1-5
    if (currentCategory === '5F' && (newCategory === '10F' || newCategory === '15F')) {
      const hasNormalDifficulty = floorSelections
        .slice(0, 5)
        .some((floor) => floor.difficulty === DUNGEON_IDX.NORMAL)

      if (hasNormalDifficulty) {
        showWarning('planner:pages.plannerMD.publish.requiresHardMode')
      }
    }

    setCategory(newCategory)
  }

  // Stable getter function - must not be recreated on each render
  const getState = () => storeApi.getState().getPlannerState()

  const {
    plannerId,
    isAutoSaving,
    isSaving,
    error: saveError,
    resolutionError,
    clearError,
    save,
    resolveConflict,
    isDirty,
    lastSavedAt,
  } = usePlannerSave({
    getState,
    subscribe: storeApi.subscribe,
    schemaVersion: config.schemaVersion,
    contentVersion: mdVersion,
    plannerType: 'MIRROR_DUNGEON',
    ...(initialPlannerId !== undefined && { initialPlannerId }),
    ...(initialSyncVersion !== undefined && { initialSyncVersion }),
    ...(initialSavedAt !== undefined && { initialSavedAt }),
    published: isPublished,
    onServerReload: handleServerReload,
    onKeepBothCreated: handleKeepBothCreated,
    syncEnabled,
  })

  // The classified error carries no timestamp, so the dialog's "detected at"
  // is minted once per conflict rather than on every render.
  const conflictState = useMemo(
    () =>
      isSyncConflict(saveError)
        ? { serverVersion: saveError.serverVersion, detectedAt: new Date().toISOString() }
        : null,
    [saveError],
  )

  // Show error toasts. The sync conflict is the one failure with a surface of
  // its own, so it is also the one that stays set instead of being reported.
  useEffect(() => {
    if (!saveError) return

    // Kept set so the dialog keeps rendering it; every other failure, including
    // the rest of the 409 codes, is reported and cleared.
    if (isSyncConflict(saveError)) return

    showAppError(saveError)
    clearError()
  }, [saveError, clearError])

  // Warn before closing the tab if changes have not reached IndexedDB yet. The
  // listener is registered for the whole mount and asks at fire time, because an
  // edit can arrive between renders and the close would then go unwarned.
  //
  // Collect the note editors' pending text first: it is the other half of what is
  // unsaved, and until it lands in the store no dirtiness check can see it. That
  // is a pull rather than each editor pushing, so no listener ordering matters.
  // `pagehide` is the mobile route out, where no warning is possible but the text
  // still has to reach the store.
  // Skip if intentional navigation (e.g., "Keep Both" conflict resolution)
  useEffect(() => {
    const handleUnload = (e: Event) => {
      noteDelivery.drain()

      // Skip warning during intentional navigation
      if (isIntentionalNavigationRef.current) return
      if (!isDirty()) return
      e.preventDefault()
      if (e.type === 'beforeunload') {
        // Older browsers gate the prompt on this rather than on preventDefault.
        ;(e as BeforeUnloadEvent).returnValue = ''
      }
    }

    window.addEventListener('beforeunload', handleUnload)
    window.addEventListener('pagehide', handleUnload)
    return () => {
      window.removeEventListener('beforeunload', handleUnload)
      window.removeEventListener('pagehide', handleUnload)
    }
  }, [isDirty, noteDelivery])

  const {
    handleImport: handleDeckImport,
    handleExport: handleDeckExport,
    pendingImport,
    clearPending,
  } = useDeckClipboard({ readDeck: () => storeApi.getState() })

  const titleByteLength = calculateByteLength(title)
  const isTitleValid = titleByteLength <= MAX_TITLE_BYTES

  const floorIndices = Array.from({ length: floorCount }, (_, i) => i)

  const handleTitleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setTitle(e.target.value)
  }

  const handleToggleDeploy = (sinnerIndex: number) => {
    const { deploymentOrder } = storeApi.getState()
    const currentIndex = deploymentOrder.indexOf(sinnerIndex)
    if (currentIndex >= 0) {
      const newOrder = [...deploymentOrder]
      newOrder.splice(currentIndex, 1)
      setDeploymentOrder(newOrder)
    } else {
      setDeploymentOrder([...deploymentOrder, sinnerIndex])
    }
  }

  const handleResetDeployment = () => {
    setDeploymentOrder([])
  }

  const handleImportConfirm = () => {
    if (!pendingImport) return

    setEquipment(pendingImport.equipment)
    setDeploymentOrder(pendingImport.deploymentOrder)

    clearPending()
    showSuccess('planner:deckBuilder.importSuccess')
  }

  // Drop the stale editor caches, then land on whichever viewer owns this plan.
  const navigateToViewer = () => {
    queryClient.removeQueries({ queryKey: plannerQueryKeys.detail(plannerId) })
    if (isPublished) {
      queryClient.removeQueries({ queryKey: publishedPlannerQueryKeys.detail(plannerId) })
      void navigate({ to: '/planner/md/gesellschaft/$id', params: { id: plannerId } })
    } else {
      void navigate({ to: '/planner/md/$id', params: { id: plannerId } })
    }
  }

  const saveThenLeave = async (saveOptions?: SaveOptions) => {
    // Mark as intentional navigation to skip "leave page?" popup
    isIntentionalNavigationRef.current = true

    const success = await save(saveOptions)
    if (!success) {
      isIntentionalNavigationRef.current = false
      return
    }

    showSuccess('planner:pages.plannerMD.save.success')
    navigateToViewer()
  }

  const handleSave = async () => {
    // A published plan whose sync is off needs the warning dialog first
    if (syncEnabled === false && isPublished) {
      setShowSaveWarning(true)
      return
    }

    await saveThenLeave()
  }

  const handleSaveWithSync = async () => {
    setShowSaveWarning(false)
    await saveThenLeave({ forceSync: true })
  }

  const handleConflictResolution = async (choice: ConflictResolutionChoice) => {
    // Mark as intentional navigation to skip "leave page?" popup
    isIntentionalNavigationRef.current = true

    const success = await resolveConflict(choice)
    if (!success) {
      // Reset intentional navigation flag if resolution failed
      isIntentionalNavigationRef.current = false
      return
    }

    showSuccess(CONFLICT_TOAST_KEY[choice])

    switch (choice) {
      case 'overwrite':
      case 'discard':
        navigateToViewer()
        break
      case 'both':
        // onKeepBothCreated navigates to the newly created planner
        break
      default:
        assertNever(choice)
    }
  }

  // ============================================================================
  // Sections, in reveal order
  // ============================================================================
  const regularSections: RevealSectionSpec[] = [
    {
      id: 'deckBuilder',
      node: (
        <>
          <Suspense fallback={<DeckGridSkeleton />}>
            <StoreBoundDeckBuilderSummary
              onToggleDeploy={handleToggleDeploy}
              onImport={handleDeckImport}
              onExport={handleDeckExport}
              onResetOrder={handleResetDeployment}
              onEditDeck={() => {
                startTransition(() => setIsDeckPaneOpen(true))
              }}
            />
          </Suspense>
          <Suspense fallback={null}>
            <DeckBuilderPane open={isDeckPaneOpen} onOpenChange={setIsDeckPaneOpen}>
              <StoreBoundDeckBuilderContent
                isActive={isDeckPaneOpen}
                onImport={handleDeckImport}
                onExport={handleDeckExport}
                onResetOrder={handleResetDeployment}
                onIdentityChange={(sinnerCode) => {
                  updateSinnerSkillEA(sinnerCode, { ...DEFAULT_SKILL_EA })
                }}
              />
            </DeckBuilderPane>
          </Suspense>
          <StoreBoundSectionNote
            sectionKey="deckBuilder"
            placeholder={t('pages.plannerMD.noteEditor.placeholder')}
          />
        </>
      ),
    },

    {
      id: 'startBuffs',
      node: (
        <Suspense fallback={<SectionBlockSkeleton />}>
          <StoreBoundStartBuffSection
            mdVersion={mdVersion}
            onClick={() => {
              setIsStartBuffPaneOpen(true)
            }}
          />
          <StartBuffEditPane
            open={isStartBuffPaneOpen}
            onOpenChange={setIsStartBuffPaneOpen}
            mdVersion={mdVersion}
          />
          <StoreBoundSectionNote
            sectionKey="startBuffs"
            placeholder={t('pages.plannerMD.noteEditor.placeholder')}
          />
        </Suspense>
      ),
    },

    {
      id: 'startGifts',
      node: (
        <Suspense fallback={<SectionBlockSkeleton />}>
          <StoreBoundStartGiftSummary
            onClick={() => {
              setIsStartGiftPaneOpen(true)
            }}
          />
          <StartGiftEditPane
            open={isStartGiftPaneOpen}
            onOpenChange={setIsStartGiftPaneOpen}
            mdVersion={mdVersion}
          />
          <StoreBoundSectionNote
            sectionKey="startGifts"
            placeholder={t('pages.plannerMD.noteEditor.placeholder')}
          />
        </Suspense>
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
            <StoreBoundEGOGiftObservationSummary
              mdVersion={mdVersion}
              onClick={() => {
                setIsObservationPaneOpen(true)
              }}
            />
          </Suspense>
          <Suspense fallback={null}>
            <EGOGiftObservationEditPane
              open={isObservationPaneOpen}
              onOpenChange={setIsObservationPaneOpen}
              mdVersion={mdVersion}
            />
          </Suspense>
          <StoreBoundSectionNote
            sectionKey="observation"
            placeholder={t('pages.plannerMD.noteEditor.placeholder')}
          />
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
            <StoreBoundSkillReplacementSection />
          </Suspense>
          <StoreBoundSectionNote
            sectionKey="skillReplacement"
            placeholder={t('pages.plannerMD.noteEditor.placeholder')}
          />
        </>
      ),
    },

    {
      id: 'comprehensiveGifts',
      node: (
        <>
          <Suspense
            fallback={
              <div className={SECTION_STYLES.panel}>
                <div className="text-center text-gray-500 py-8">
                  {t('pages.plannerMD.loading.EGOGiftData')}
                </div>
              </div>
            }
          >
            <StoreBoundComprehensiveGiftSummary onClick={() => setIsComprehensivePaneOpen(true)} />
          </Suspense>
          <Suspense fallback={null}>
            <ComprehensiveGiftSelectorPane
              open={isComprehensivePaneOpen}
              onOpenChange={setIsComprehensivePaneOpen}
            />
          </Suspense>
          <StoreBoundSectionNote
            sectionKey="comprehensiveGifts"
            placeholder={t('pages.plannerMD.noteEditor.placeholder')}
          />
        </>
      ),
    },
  ]

  const regularSectionCount = regularSections.length

  /** Slot of the first floor block; the floors take the slots after the regular sections. */
  const floorSlotStart = regularSectionCount + 1

  const sections: RevealSectionSpec[] = [
    ...regularSections,
    {
      // Unlike the others this entry spans one reveal slot per floor of the category
      id: 'floorThemes',
      node: (
        <PlannerSection title={t('pages.plannerMD.floorThemes')}>
          <Suspense
            fallback={
              <div className="text-center text-gray-500 py-8">
                {t('pages.plannerMD.loading.themePackData')}
              </div>
            }
          >
            <div className="space-y-4">
              {floorIndices.map((floorIndex) => {
                const sectionIndex = floorSlotStart + floorIndex
                if (visibleSections < sectionIndex) return null

                const floorNumber = floorIndex + 1
                const floorNoteKey = `floor-${floorIndex}`
                return (
                  <div key={floorIndex} className="space-y-2">
                    <FloorThemeGiftSection floorNumber={floorNumber} floorIndex={floorIndex} />
                    <StoreBoundSectionNote
                      sectionKey={floorNoteKey}
                      placeholder={t('pages.plannerMD.noteEditor.placeholder')}
                    />
                  </div>
                )
              })}
            </div>
          </Suspense>
        </PlannerSection>
      ),
    },
  ]

  const totalSections = regularSectionCount + floorCount

  // Progressive section rendering
  useEffect(() => {
    if (visibleSections < totalSections) {
      const rafId = requestAnimationFrame(() => {
        setVisibleSections(visibleSections + 1)
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [visibleSections, totalSections, setVisibleSections])

  // Reduce visible sections when category changes to fewer floors
  useEffect(() => {
    const newTotalSections = regularSectionCount + FLOOR_COUNTS[category]
    if (visibleSections > newTotalSections) {
      setVisibleSections(newTotalSections)
    }
  }, [category, visibleSections, setVisibleSections, regularSectionCount])

  return (
    <NoteDeliveryProvider registry={noteDelivery}>
      <div className={SECTION_STYLES.LAYOUT.page}>
        <ConflictResolutionDialog
          open={isSyncConflict(saveError)}
          conflictState={conflictState}
          resolutionError={resolutionError}
          onChoice={handleConflictResolution}
          isResolving={isSaving}
        />

        <SyncOffWarningDialog
          action="save"
          open={showSaveWarning}
          onOpenChange={setShowSaveWarning}
          onConfirm={handleSaveWithSync}
          isPending={isSaving}
        />

        <div className="flex items-center justify-end gap-2 mb-4">
          {isAutoSaving ? (
            <span className={SECTION_STYLES.TEXT.caption}>
              {t('pages.plannerMD.save.autoSaving', 'Saving...')}
              <LastSavedLabel lastSavedAt={lastSavedAt} inline />
            </span>
          ) : (
            <LastSavedLabel lastSavedAt={lastSavedAt} />
          )}
          <Button onClick={handleSave} disabled={isSaving} variant="outline">
            <Save className="w-4 h-4 mr-2" />
            {isSaving ? t('pages.plannerMD.save.saving') : t('pages.plannerMD.save.button')}
          </Button>
        </div>

        <div className="bg-background rounded-lg space-y-2">
          <div className="flex flex-col sm:flex-row gap-6 sm:gap-4 items-start">
            <div className="flex flex-col sm:flex-row sm:items-start gap-2 h-12">
              <label className="text-sm font-medium whitespace-nowrap sm:mt-2">
                {t('pages.plannerMD.category')}
              </label>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="outline" className="w-auto min-w-24 h-10 justify-between">
                    <MdCategoryLabel category={category} />
                    <ChevronDown className="ml-2 h-4 w-4" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start">
                  {MD_CATEGORIES.map((cat) => (
                    <DropdownMenuItem
                      key={cat}
                      onClick={() => {
                        handleCategoryChange(cat)
                      }}
                    >
                      <MdCategoryLabel category={cat} />
                    </DropdownMenuItem>
                  ))}
                </DropdownMenuContent>
              </DropdownMenu>
            </div>

            <div className="flex flex-col sm:flex-row sm:items-start gap-2 w-full sm:w-auto">
              <label className="text-sm font-medium whitespace-nowrap sm:mt-2">
                {t('pages.plannerMD.keywords')}
              </label>
              <div className="w-full sm:w-80">
                <KeywordSelector
                  options={PLANNER_KEYWORDS}
                  selectedOptions={selectedKeywords}
                  onSelectionChange={setSelectedKeywords}
                  getIconPath={getKeywordIconPath}
                  placeholder={t('pages.plannerMD.keywordsPlaceholder')}
                  clearLabel={t('pages.plannerMD.clearKeywords')}
                  selectedCountText={t('pages.plannerMD.keywordSelector.selected', {
                    count: selectedKeywords.size,
                  })}
                />
              </div>
            </div>
          </div>

          <div className="flex flex-col sm:flex-row sm:items-start gap-2">
            <label className="text-sm font-medium whitespace-nowrap sm:mt-2">
              {t('pages.plannerMD.planTitle')}
            </label>
            <div className="flex flex-col gap-1 flex-1">
              <input
                type="text"
                value={title}
                onChange={handleTitleChange}
                placeholder={t('pages.plannerMD.titlePlaceholder')}
                className={`w-full px-3 py-2 border rounded-md bg-background ${
                  !isTitleValid ? 'border-destructive' : 'border-border'
                } focus:outline-none focus:ring-2 focus:ring-primary`}
              />
              <span
                className={`text-xs text-right ${
                  !isTitleValid ? 'text-destructive' : 'text-muted-foreground'
                }`}
              >
                {titleByteLength}/{MAX_TITLE_BYTES} {t('pages.plannerMD.bytes')}
              </span>
            </div>
          </div>

          <PlannerSection title={t('pages.plannerMD.introduction')}>
            <StoreBoundSectionNote
              sectionKey="intro"
              placeholder={t('pages.plannerMD.noteEditor.placeholder')}
            />
          </PlannerSection>

          <DeckImportConfirmDialog
            pendingImport={pendingImport}
            onConfirm={handleImportConfirm}
            onCancel={clearPending}
          />

          {sections.map((section, index) => (
            <RevealSection key={section.id} visible={visibleSections >= index + 1}>
              {section.node}
            </RevealSection>
          ))}

          <PlannerSection title={t('pages.plannerMD.closingNotes')}>
            <StoreBoundSectionNote
              sectionKey="outro"
              placeholder={t('pages.plannerMD.noteEditor.placeholder')}
            />
          </PlannerSection>

          <div className="flex justify-end gap-2 pt-6 border-t">
            <Button onClick={handleSave} disabled={isSaving} variant="outline">
              <Save className="w-4 h-4 mr-2" />
              {isSaving ? t('pages.plannerMD.save.saving') : t('pages.plannerMD.save.button')}
            </Button>
          </div>
        </div>
      </div>
    </NoteDeliveryProvider>
  )
}
