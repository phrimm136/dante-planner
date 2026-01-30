// React core
import { useState, useEffect, Suspense, startTransition, useCallback, useRef } from 'react'

// TanStack
import { useNavigate } from '@tanstack/react-router'

// Third-party libraries
import { useTranslation } from 'react-i18next'
import { ChevronDown, Save } from 'lucide-react'
import { toast } from 'sonner'
import { formatDistanceToNow } from 'date-fns'
import { enUS, ja, ko, zhCN } from 'date-fns/locale'

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
import { MD_CATEGORIES, PLANNER_KEYWORDS, FLOOR_COUNTS, MAX_NOTE_BYTES, DUNGEON_IDX } from '@/lib/constants'
import { getKeywordIconPath } from '@/lib/assetPaths'
import { getKeywordDisplayName, calculateByteLength } from '@/lib/utils'
import { encodeDeckCode, decodeDeckCode, validateDeckCode } from '@/lib/deckCode'

// Project types & schemas
import type { MDCategory } from '@/lib/constants'
import type { NoteContent } from '@/types/NoteEditorTypes'
import type { SaveablePlanner, MDPlannerContent } from '@/types/PlannerTypes'
import type { DecodedDeck } from '@/lib/deckCode'

// Store
import {
  usePlannerEditorStore,
  usePlannerEditorStoreApi,
} from '@/stores/usePlannerEditorStore'

// Project hooks
import { useIdentityListSpec } from '@/hooks/useIdentityListData'
import { useEGOListSpec } from '@/hooks/useEGOListData'
import { usePlannerSave } from '@/hooks/usePlannerSave'
import { usePlannerConfig } from '@/hooks/usePlannerConfig'
import { useUserSettingsQuery } from '@/hooks/useUserSettings'

// Project components (@/components)
import { DeckBuilderSummary } from '@/components/deckBuilder/DeckBuilderSummary'
import { DeckBuilderPane } from '@/components/deckBuilder/DeckBuilderPane'
import { StartBuffSection } from '@/components/startBuff/StartBuffSection'
import { StartBuffEditPane } from '@/components/startBuff/StartBuffEditPane'
import { StartGiftSummary } from '@/components/startGift/StartGiftSummary'
import { StartGiftEditPane } from '@/components/startGift/StartGiftEditPane'
import { EGOGiftObservationSummary } from '@/components/egoGift/EGOGiftObservationSummary'
import { EGOGiftObservationEditPane } from '@/components/egoGift/EGOGiftObservationEditPane'
import { ComprehensiveGiftSummary } from '@/components/egoGift/ComprehensiveGiftSummary'
import { ComprehensiveGiftSelectorPane } from '@/components/egoGift/ComprehensiveGiftSelectorPane'
import { SkillReplacementSection } from '@/components/skillReplacement/SkillReplacementSection'
import { FloorThemeGiftSection } from '@/components/floorTheme/FloorThemeGiftSection'
import { PlannerSection } from '@/components/common/PlannerSection'
import { NoteEditor } from '@/components/noteEditor/NoteEditor'
import { ConflictResolutionDialog } from '@/components/planner/ConflictResolutionDialog'

const MAX_TITLE_BYTES = 256

interface PlannerMDEditorContentProps {
  mode: 'new' | 'edit'
  planner?: SaveablePlanner
}

interface KeywordSelectorProps {
  options: readonly string[]
  selectedOptions: Set<string>
  onSelectionChange: (options: Set<string>) => void
  getIconPath: (option: string) => string
  placeholder: string
  clearLabel: string
  selectedCountText: string
}

function KeywordSelector({
  options,
  selectedOptions,
  onSelectionChange,
  getIconPath,
  placeholder,
  clearLabel,
  selectedCountText,
}: KeywordSelectorProps) {
  const toggleOption = (option: string) => {
    const newSelection = new Set(selectedOptions)
    if (newSelection.has(option)) {
      newSelection.delete(option)
    } else {
      newSelection.add(option)
    }
    onSelectionChange(newSelection)
  }

  const clearAll = () => {
    onSelectionChange(new Set())
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className="min-h-10 w-full p-2 border border-border rounded-md bg-card cursor-pointer hover:border-primary/50 transition-colors flex items-center"
        >
          {selectedOptions.size === 0 ? (
            <span className="text-muted-foreground text-sm">{placeholder}</span>
          ) : (
            <div className="flex flex-wrap gap-1">
              {Array.from(selectedOptions).map((option) => (
                <div
                  key={option}
                  className="w-7 h-7 rounded-md border-2 border-primary bg-primary/10"
                  title={getKeywordDisplayName(option)}
                >
                  <img
                    src={getIconPath(option)}
                    alt={getKeywordDisplayName(option)}
                    className="w-full h-full object-contain"
                  />
                </div>
              ))}
            </div>
          )}
          <ChevronDown className="ml-auto size-4 opacity-50 shrink-0" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-80 p-3">
        <div className="flex justify-between items-center mb-2">
          <span className="text-sm text-muted-foreground">
            {selectedCountText}
          </span>
          <Button variant="ghost" size="sm" onClick={clearAll}>
            {clearLabel}
          </Button>
        </div>

        <div className="flex flex-wrap gap-2">
          {options.map((option) => {
            const isSelected = selectedOptions.has(option)
            const label = getKeywordDisplayName(option)
            return (
              <button
                type="button"
                key={option}
                onClick={() => { toggleOption(option); }}
                className={`shrink-0 w-10 h-10 rounded-md border-2 transition-all ${
                  isSelected
                    ? 'border-primary bg-primary/10'
                    : 'border-border bg-button hover:border-primary/50'
                }`}
                title={label}
              >
                <img
                  src={getIconPath(option)}
                  alt={label}
                  className="w-full h-full object-contain"
                />
              </button>
            )
          })}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export function PlannerMDEditorContent({ mode, planner }: PlannerMDEditorContentProps) {
  const { t, i18n } = useTranslation(['planner', 'common'])

  // Map i18n language to date-fns locale
  const dateFnsLocale = {
    EN: enUS,
    JP: ja,
    KR: ko,
    CN: zhCN,
  }[i18n.language] ?? enUS
  const config = usePlannerConfig()
  const navigate = useNavigate()

  // Get user settings for sync preference
  const { data: userSettings } = useUserSettingsQuery()
  const syncEnabled = userSettings?.syncEnabled ?? false

  // Ref to skip beforeunload warning during intentional navigation (e.g., "Keep Both")
  const isIntentionalNavigationRef = useRef(false)

  // Callback for "Keep Both" - navigate to the newly created copy
  const handleKeepBothCreated = useCallback((newPlannerId: string) => {
    // Mark as intentional navigation to skip "leave page?" popup
    isIntentionalNavigationRef.current = true

    // Navigate to forked planner edit page, replacing current history entry
    // Back button will go to original view (which now shows server version)
    navigate({ to: '/planner/md/$id/edit', params: { id: newPlannerId }, replace: true })
  }, [navigate])

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
  const sectionNotes = usePlannerEditorStore((s) => s.sectionNotes)
  const updateSectionNote = usePlannerEditorStore((s) => s.updateSectionNote)
  const selectedKeywords = usePlannerEditorStore((s) => s.selectedKeywords)
  const setSelectedKeywords = usePlannerEditorStore((s) => s.setSelectedKeywords)

  // Actions (stable references, no re-render)
  const setEquipment = usePlannerEditorStore((s) => s.setEquipment)
  const setDeploymentOrder = usePlannerEditorStore((s) => s.setDeploymentOrder)
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
  const handleServerReload = useCallback((reloadedPlanner: SaveablePlanner) => {
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
  }, [initializeFromPlannerAction, t])

  // ============================================================================
  // Section Note Handler
  // ============================================================================
  const handleSectionNoteChange = useCallback((sectionKey: string, content: NoteContent) => {
    updateSectionNote(sectionKey, content)
  }, [updateSectionNote])

  // ============================================================================
  // Category Change Handler
  // ============================================================================
  const handleCategoryChange = useCallback((newCategory: MDCategory) => {
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
  }, [storeApi, setCategory, t])

  // Stable getter function - must not be recreated on each render
  const getState = useCallback(() => storeApi.getState().getPlannerState(), [storeApi])

  const {
    plannerId,
    isAutoSaving,
    isSaving,
    errorCode,
    errorI18nKey,
    errorI18nParams,
    conflictState,
    clearError,
    save,
    resolveConflict,
    hasLocalUnsavedChanges,
    lastSavedAt,
  } = usePlannerSave({
    getState,
    subscribe: storeApi.subscribe,
    schemaVersion: config.schemaVersion,
    contentVersion: config.mdCurrentVersion,
    plannerType: 'MIRROR_DUNGEON',
    initialPlannerId: (() => {
      const id = mode === 'edit' && planner?.metadata.id ? planner.metadata.id : undefined
      return id
    })(),
    initialSyncVersion: mode === 'edit' && planner?.metadata.syncVersion !== undefined ? planner.metadata.syncVersion : undefined,
    initialSavedAt: mode === 'edit' && planner?.metadata.lastModifiedAt ? planner.metadata.lastModifiedAt : undefined,
    published: isPublished,
    onServerReload: handleServerReload,
    onKeepBothCreated: handleKeepBothCreated,
    syncEnabled,
  })

  // Show error toasts
  useEffect(() => {
    if (!errorCode) return

    if (errorCode === 'banned') {
      toast.error(t('moderation.banned', { ns: 'common' }))
      clearError()
    } else if (errorCode === 'timedOut') {
      toast.error(t('moderation.timedOut', { ns: 'common' }))
      clearError()
    } else if (errorCode === 'saveFailed') {
      // Use user-friendly i18n key if available, otherwise generic error
      const message = errorI18nKey ? t(errorI18nKey, errorI18nParams ?? {}) : t('pages.plannerMD.save.failed')
      toast.error(message)
      clearError()
    } else if (errorCode === 'quotaExceeded') {
      toast.error(t('pages.plannerMD.save.quotaExceeded', 'Storage quota exceeded'))
      clearError()
    }
  }, [errorCode, errorI18nKey, errorI18nParams, clearError, t])

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

  const handleSave = async () => {
    // Mark as intentional navigation to skip "leave page?" popup
    isIntentionalNavigationRef.current = true

    const success = await save()
    if (success) {
      toast.success(t('pages.plannerMD.save.success'))
      // Navigate to appropriate viewer page based on published status
      if (isPublished) {
        void navigate({ to: '/planner/md/gesellschaft/$id', params: { id: plannerId } })
      } else {
        void navigate({ to: '/planner/md/$id', params: { id: plannerId } })
      }
    } else {
      // Reset intentional navigation flag if save failed
      isIntentionalNavigationRef.current = false
    }
  }

  return (
    <div className="container mx-auto p-8">
      <ConflictResolutionDialog
        open={errorCode === 'conflict'}
        conflictState={conflictState}
        onChoice={resolveConflict}
        isResolving={isSaving}
      />

      <div className="flex items-center justify-end gap-2 mb-4">
          {isAutoSaving && (
            <span className="text-sm text-muted-foreground">
              {t('pages.plannerMD.save.autoSaving', 'Saving...')}
              {lastSavedAt && (() => {
                try {
                  const parsedDate = new Date(lastSavedAt)
                  if (isNaN(parsedDate.getTime())) return null
                  return ` - ${t('sync.lastSaved', { time: formatDistanceToNow(parsedDate, { addSuffix: true, locale: dateFnsLocale }) })}`
                } catch {
                  return null
                }
              })()}
            </span>
          )}
          {!isAutoSaving && lastSavedAt && (() => {
            try {
              const parsedDate = new Date(lastSavedAt)
              if (isNaN(parsedDate.getTime())) return null
              return (
                <span className="text-sm text-muted-foreground">
                  {t('sync.lastSaved', { time: formatDistanceToNow(parsedDate, { addSuffix: true, locale: dateFnsLocale }) })}
                </span>
              )
            } catch {
              return null
            }
          })()}
          <Button onClick={handleSave} disabled={isSaving} variant="outline">
            <Save className="w-4 h-4 mr-2" />
            {isSaving ? t('pages.plannerMD.save.saving') : t('pages.plannerMD.save.button')}
          </Button>
      </div>

      <div className="bg-background rounded-lg space-y-2">
        <div className="flex flex-col sm:flex-row gap-6 sm:gap-4 items-start">
          <div className="flex flex-col sm:flex-row sm:items-start gap-2 h-12">
            <label className="text-sm font-medium whitespace-nowrap sm:mt-2">{t('pages.plannerMD.category')}</label>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" className="w-auto min-w-24 h-10 justify-between">
                  {t(`pages.plannerList.mdCategory.${category}`)}
                  <ChevronDown className="ml-2 h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start">
                {MD_CATEGORIES.map((cat) => (
                  <DropdownMenuItem key={cat} onClick={() => { handleCategoryChange(cat); }}>
                    {t(`pages.plannerList.mdCategory.${cat}`)}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>

          <div className="flex flex-col sm:flex-row sm:items-start gap-2 w-full sm:w-auto">
            <label className="text-sm font-medium whitespace-nowrap sm:mt-2">{t('pages.plannerMD.keywords')}</label>
            <div className="w-full sm:w-80">
              <KeywordSelector
                options={PLANNER_KEYWORDS}
                selectedOptions={selectedKeywords}
                onSelectionChange={setSelectedKeywords}
                getIconPath={getKeywordIconPath}
                placeholder={t('pages.plannerMD.keywordsPlaceholder')}
                clearLabel={t('pages.plannerMD.clearKeywords')}
                selectedCountText={t('pages.plannerMD.keywordSelector.selected', { count: selectedKeywords.size })}
              />
            </div>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row sm:items-start gap-2">
          <label className="text-sm font-medium whitespace-nowrap sm:mt-2">{t('pages.plannerMD.planTitle')}</label>
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

        {visibleSections >= 1 && (
          <>
            <Suspense
              fallback={
                <div className="space-y-2">
                  <div className="border-2 border-border rounded-lg p-4">
                    <div className="flex flex-wrap gap-2">
                      {Array.from({ length: 12 }).map((_, i) => (
                        <Skeleton
                          key={i}
                          className="w-16 h-20 rounded-md"
                          style={{ animationDelay: `${i * 40}ms` }}
                        />
                      ))}
                    </div>
                  </div>
                </div>
              }
            >
              <DeckBuilderSummary
                onToggleDeploy={handleToggleDeploy}
                onImport={handleDeckImport}
                onExport={handleDeckExport}
                onResetOrder={handleResetDeployment}
                onEditDeck={() => { startTransition(() => setIsDeckPaneOpen(true)) }}
              />
            </Suspense>
            <Suspense fallback={null}>
              <DeckBuilderPane
                open={isDeckPaneOpen}
                onOpenChange={setIsDeckPaneOpen}
                onImport={handleDeckImport}
                onExport={handleDeckExport}
                onResetOrder={handleResetDeployment}
              />
            </Suspense>
          </>
        )}

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
              <Button variant="outline" onClick={handleImportCancel}>
                {t('deckBuilder.cancel')}
              </Button>
              <Button onClick={handleImportConfirm}>
                {t('deckBuilder.apply')}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {visibleSections >= 1 && (
          <NoteEditor
            value={sectionNotes.deckBuilder}
            onChange={(content) => { handleSectionNoteChange('deckBuilder', content); }}
            placeholder={t('pages.plannerMD.noteEditor.placeholder')}
            maxBytes={MAX_NOTE_BYTES}
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
            <StartBuffSection
              mdVersion={config.mdCurrentVersion}
              onClick={() => { setIsStartBuffPaneOpen(true); }}
            />
            <StartBuffEditPane
              open={isStartBuffPaneOpen}
              onOpenChange={setIsStartBuffPaneOpen}
              mdVersion={config.mdCurrentVersion}
            />
            <NoteEditor
              value={sectionNotes.startBuffs}
              onChange={(content) => { handleSectionNoteChange('startBuffs', content); }}
              placeholder={t('pages.plannerMD.noteEditor.placeholder')}
              maxBytes={MAX_NOTE_BYTES}
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
            <StartGiftSummary
              onClick={() => { setIsStartGiftPaneOpen(true); }}
            />
            <StartGiftEditPane
              open={isStartGiftPaneOpen}
              onOpenChange={setIsStartGiftPaneOpen}
              mdVersion={config.mdCurrentVersion}
            />
            <NoteEditor
              value={sectionNotes.startGifts}
              onChange={(content) => { handleSectionNoteChange('startGifts', content); }}
              placeholder={t('pages.plannerMD.noteEditor.placeholder')}
              maxBytes={MAX_NOTE_BYTES}
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
                      <div className="flex items-center gap-1">
                        <Skeleton className="w-8 h-8 rounded-md" />
                        <Skeleton className="w-12 h-6" />
                      </div>
                    </div>
                    <div className="flex flex-wrap gap-2 p-2 min-h-28">
                      {Array.from({ length: 6 }).map((_, i) => (
                        <Skeleton
                          key={i}
                          className="w-24 h-24 rounded-md"
                          style={{ animationDelay: `${i * 80}ms` }}
                        />
                      ))}
                    </div>
                  </div>
                </PlannerSection>
              }
            >
              <EGOGiftObservationSummary
                onClick={() => { setIsObservationPaneOpen(true); }}
              />
            </Suspense>
            <Suspense fallback={null}>
              <EGOGiftObservationEditPane
                open={isObservationPaneOpen}
                onOpenChange={setIsObservationPaneOpen}
              />
            </Suspense>
            <NoteEditor
              value={sectionNotes.observation}
              onChange={(content) => { handleSectionNoteChange('observation', content); }}
              placeholder={t('pages.plannerMD.noteEditor.placeholder')}
              maxBytes={MAX_NOTE_BYTES}
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
                        style={{ animationDelay: `${i * 60}ms` }}
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
              <SkillReplacementSection />
            </Suspense>
            <NoteEditor
              value={sectionNotes.skillReplacement}
              onChange={(content) => { handleSectionNoteChange('skillReplacement', content); }}
              placeholder={t('pages.plannerMD.noteEditor.placeholder')}
              maxBytes={MAX_NOTE_BYTES}
            />
          </>
        )}

        {visibleSections >= 6 && (
          <>
            <Suspense
              fallback={
                <div className="bg-muted border border-border rounded-md p-6">
                  <div className="text-center text-gray-500 py-8">
                    {t('pages.plannerMD.loading.EGOGiftData')}
                  </div>
                </div>
              }
            >
              <ComprehensiveGiftSummary
                onClick={() => setIsComprehensivePaneOpen(true)}
              />
            </Suspense>
            <Suspense fallback={null}>
              <ComprehensiveGiftSelectorPane
                open={isComprehensivePaneOpen}
                onOpenChange={setIsComprehensivePaneOpen}
              />
            </Suspense>
            <NoteEditor
              value={sectionNotes.comprehensiveGifts}
              onChange={(content) => { handleSectionNoteChange('comprehensiveGifts', content); }}
              placeholder={t('pages.plannerMD.noteEditor.placeholder')}
              maxBytes={MAX_NOTE_BYTES}
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
                      <FloorThemeGiftSection
                        floorNumber={floorNumber}
                        floorIndex={floorIndex}
                      />
                      <NoteEditor
                        value={sectionNotes[floorNoteKey]}
                        onChange={(content) => { handleSectionNoteChange(floorNoteKey, content); }}
                        placeholder={t('pages.plannerMD.noteEditor.placeholder')}
                        maxBytes={MAX_NOTE_BYTES}
                      />
                    </div>
                  )
                })}
              </div>
            </Suspense>
          </PlannerSection>
        )}

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
