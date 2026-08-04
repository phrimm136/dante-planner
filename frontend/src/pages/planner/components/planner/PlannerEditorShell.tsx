// React core
import { useState, useEffect, Suspense, startTransition, useRef } from 'react'

// TanStack
import { useNavigate } from '@tanstack/react-router'
import { queryClient } from '@/lib/queryClient'
import { plannerQueryKeys } from '../../lib/plannerQueryKeys'
import { publishedPlannerQueryKeys } from '../../hooks/usePublishedPlannerQuery'

// Third-party libraries
import { useTranslation } from 'react-i18next'
import { ChevronDown, Save } from 'lucide-react'
import { toast } from '@/lib/toast'

// shadcn/ui components
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
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
import { STAGGER_STEP_MS, SECTION_STYLES } from '@/lib/constants'
import { getKeywordIconPath } from '@/shared/assets'
import { assertNever, calculateByteLength } from '@/lib/utils'
import { encodeDeckCode, decodeDeckCode, validateDeckCode } from '../../lib/deckCode'
import { CONFLICT_TOAST_KEY } from '../../lib/conflictChoice'
import { saveErrorMessage } from '../../lib/plannerSaveErrors'

// Project types & schemas
import type { MDCategory } from '@/shared/gameData'
import type {
  SaveablePlanner,
  MDPlannerContent,
  ConflictResolutionChoice,
} from '../../types/PlannerTypes'
import type { DecodedDeck } from '../../lib/deckCode'

// Store
import { usePlannerEditorStore, usePlannerEditorStoreApi } from '../../stores/usePlannerEditorStore'

// Project hooks
import { useIdentityListSpec } from '@/pages/identity'
import { useEGOListSpec } from '@/pages/ego'
import { usePlannerSave } from '../../hooks/usePlannerSave'
import type { SaveOptions } from '../../hooks/usePlannerSave'
import { usePlannerConfig } from '../../hooks/usePlannerConfig'
import { useUserSettingsQuery } from '@/pages/settings'

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
import { PlannerSection } from '../PlannerSection'
import { StoreBoundSectionNote } from './StoreBoundSectionNote'
import { ConflictResolutionDialog } from './ConflictResolutionDialog'
import { SyncOffWarningDialog } from '../SyncOffWarningDialog'
import { KeywordSelector } from './KeywordSelector'
import { LastSavedLabel } from './LastSavedLabel'
import { staggerDelay } from '@/lib/stagger'

const MAX_TITLE_BYTES = 256
const SAVE_FAILED_TOAST_KEY = 'pages.plannerMD.save.failed'

/**
 * The parts of an editing session the shell cannot derive for itself: which
 * planner is being edited, at which content and sync version.
 */
export interface PlannerEditorSession {
  /** Game content version the planner is authored against. */
  contentVersion: number
  /** Existing planner id; absent means the shell mints one. */
  initialPlannerId?: string
  /** Server version to present on the next sync. */
  initialSyncVersion?: number
  /** Timestamp to seed the "last saved" label with. */
  initialSavedAt?: string
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
  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [pendingImport, setPendingImport] = useState<DecodedDeck | null>(null)
  const [showSaveWarning, setShowSaveWarning] = useState(false)

  // ============================================================================
  // Derived State
  // ============================================================================
  const floorCount = FLOOR_COUNTS[category]
  const totalSections = 6 + floorCount

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
    const newTotalSections = 6 + FLOOR_COUNTS[category]
    if (visibleSections > newTotalSections) {
      setVisibleSections(newTotalSections)
    }
  }, [category, visibleSections, setVisibleSections])

  // ============================================================================
  // SSE Reload Handler - Uses store batch action
  // ============================================================================
  const handleServerReload = (reloadedPlanner: SaveablePlanner) => {
    if (reloadedPlanner.config.type !== 'MIRROR_DUNGEON') {
      console.error('Attempted to load non-MD planner in MD editor:', reloadedPlanner.config.type)
      toast.error(t('pages.plannerMD.errors.invalidType', 'Cannot load: Invalid planner type'))
      return
    }

    const content = reloadedPlanner.content as MDPlannerContent
    initializeFromPlannerAction(content, {
      title: reloadedPlanner.metadata.title,
      category: reloadedPlanner.config.category as MDCategory,
      isPublished: reloadedPlanner.metadata.published ?? false,
    })
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
        toast.warning(t('pages.plannerMD.publish.requiresHardMode'))
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
    clearError,
    save,
    resolveConflict,
    hasLocalUnsavedChanges,
    lastSavedAt,
  } = usePlannerSave({
    getState,
    subscribe: storeApi.subscribe,
    schemaVersion: config.schemaVersion,
    contentVersion: mdVersion,
    plannerType: 'MIRROR_DUNGEON',
    initialPlannerId,
    initialSyncVersion,
    initialSavedAt,
    published: isPublished,
    onServerReload: handleServerReload,
    onKeepBothCreated: handleKeepBothCreated,
    syncEnabled,
  })

  // Show error toasts. Errors with their own surface (conflict dialog) or with
  // none by design (sync paused) yield no message and stay set.
  useEffect(() => {
    if (!saveError) return

    const message = saveErrorMessage(saveError, SAVE_FAILED_TOAST_KEY)
    if (!message) return

    toast.error(message)
    clearError()
  }, [saveError, clearError])

  // Warn before closing tab only if there are unsaved local changes (not yet auto-saved to IndexedDB)
  // Skip if intentional navigation (e.g., "Keep Both" conflict resolution)
  useEffect(() => {
    if (!hasLocalUnsavedChanges) return

    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      // Skip warning during intentional navigation
      if (isIntentionalNavigationRef.current) return
      e.preventDefault()
      e.returnValue = ''
    }

    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [hasLocalUnsavedChanges])

  const identitySpec = useIdentityListSpec()
  const egoSpec = useEGOListSpec()

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

  const handleDeckExport = async () => {
    try {
      const { equipment, deploymentOrder } = storeApi.getState()
      const code = encodeDeckCode(equipment, deploymentOrder)
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
    toast.success(t('deckBuilder.importSuccess'))
  }

  const handleImportCancel = () => {
    setImportDialogOpen(false)
    setPendingImport(null)
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

    toast.success(t('pages.plannerMD.save.success'))
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
      toast.error(t(SAVE_FAILED_TOAST_KEY))
      return
    }

    toast.success(t(CONFLICT_TOAST_KEY[choice]))

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

  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <ConflictResolutionDialog
        open={saveError?.kind === 'conflict'}
        conflictState={saveError?.kind === 'conflict' ? saveError.state : null}
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
                  {t(`pages.plannerList.mdCategory.${category}`)}
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
                    {t(`pages.plannerList.mdCategory.${cat}`)}
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

        {visibleSections >= 1 && (
          <>
            <Suspense
              fallback={
                <div className="space-y-2">
                  <div className="border-2 border-border rounded-lg p-4">
                    <div className={SECTION_STYLES.LAYOUT.wrap}>
                      {Array.from({ length: 12 }).map((_, i) => (
                        <Skeleton
                          key={i}
                          className="w-16 h-20 rounded-md"
                          style={staggerDelay(i)}
                        />
                      ))}
                    </div>
                  </div>
                </div>
              }
            >
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
          </>
        )}

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
              <Button variant="outline" onClick={handleImportCancel}>
                {t('deckBuilder.cancel')}
              </Button>
              <Button onClick={handleImportConfirm}>{t('deckBuilder.apply')}</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {visibleSections >= 1 && (
          <StoreBoundSectionNote
            sectionKey="deckBuilder"
            placeholder={t('pages.plannerMD.noteEditor.placeholder')}
          />
        )}

        {visibleSections >= 2 && (
          <Suspense
            fallback={
              <div className="space-y-2">
                <Skeleton className="h-32 w-full rounded-lg" />
              </div>
            }
          >
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
        )}

        {visibleSections >= 3 && (
          <Suspense
            fallback={
              <div className="space-y-2">
                <Skeleton className="h-32 w-full rounded-lg" />
              </div>
            }
          >
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
        )}

        {visibleSections >= 4 && (
          <>
            <Suspense
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
        )}

        {visibleSections >= 5 && (
          <>
            <Suspense
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
              <StoreBoundSkillReplacementSection />
            </Suspense>
            <StoreBoundSectionNote
              sectionKey="skillReplacement"
              placeholder={t('pages.plannerMD.noteEditor.placeholder')}
            />
          </>
        )}

        {visibleSections >= 6 && (
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
              <StoreBoundComprehensiveGiftSummary
                onClick={() => setIsComprehensivePaneOpen(true)}
              />
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
        )}

        {visibleSections >= 7 && (
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
                  const sectionIndex = 7 + floorIndex
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
        )}

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
  )
}
