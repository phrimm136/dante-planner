import { useState, useMemo, useEffect, Suspense, startTransition } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronDown, Save, Upload } from 'lucide-react'
import { toast } from 'sonner'
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
import { MD_CATEGORIES, PLANNER_KEYWORDS, SINNERS, MAX_LEVEL, DEFAULT_SKILL_EA, FLOOR_COUNTS, DUNGEON_IDX, MAX_NOTE_BYTES } from '@/lib/constants'
import type { MDCategory, DungeonIdx } from '@/lib/constants'
import { getPlannerKeywordIconPath } from '@/lib/assetPaths'
import { getKeywordDisplayName, calculateByteLength } from '@/lib/utils'
import { validateFloorThemePacksForSave } from '@/lib/plannerHelpers'
import { DeckBuilderSummary } from '@/components/deckBuilder/DeckBuilderSummary'
import { DeckBuilderPane } from '@/components/deckBuilder/DeckBuilderPane'
import { encodeDeckCode, decodeDeckCode, validateDeckCode, type DecodedDeck } from '@/lib/deckCode'
import { useIdentityListSpec } from '@/hooks/useIdentityListData'
import { useEGOListSpec } from '@/hooks/useEGOListData'
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
import type { SinnerEquipment, SkillEAState, DeckFilterState } from '@/types/DeckTypes'
import type { FloorThemeSelection } from '@/types/ThemePackTypes'
import type { NoteContent } from '@/types/NoteEditorTypes'
import type { SaveablePlanner, MDPlannerContent } from '@/types/PlannerTypes'
import { createEmptyNoteContent } from '@/schemas/NoteEditorSchemas'
import { deserializeSets } from '@/schemas/PlannerSchemas'
import { usePlannerStorage } from '@/hooks/usePlannerStorage'
import { usePlannerSave } from '@/hooks/usePlannerSave'
import { usePlannerConfig } from '@/hooks/usePlannerConfig'
import { ConflictResolutionDialog } from '@/components/planner/ConflictResolutionDialog'
import type { PlannerState } from '@/hooks/usePlannerSave'
import { plannerApi } from '@/lib/plannerApi'
import { useAuthQuery } from '@/hooks/useAuthQuery'

const MAX_TITLE_BYTES = 256

interface KeywordSelectorProps {
  options: readonly string[]
  selectedOptions: Set<string>
  onSelectionChange: (options: Set<string>) => void
  getIconPath: (option: string) => string
  placeholder: string
  clearLabel: string
  selectedCountText: string
  doneLabel: string
}

function KeywordSelector({
  options,
  selectedOptions,
  onSelectionChange,
  getIconPath,
  placeholder,
  clearLabel,
  selectedCountText,
  doneLabel,
}: KeywordSelectorProps) {
  const [isOpen, setIsOpen] = useState(false)

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
    <div className="space-y-2">
      {/* Selected Keywords Display */}
      <div
        onClick={() => { setIsOpen(!isOpen); }}
        className="min-h-8 p-2 border border-border rounded-md bg-card cursor-pointer hover:border-primary/50 transition-colors"
      >
        {selectedOptions.size === 0 ? (
          <span className="text-muted-foreground text-sm">{placeholder}</span>
        ) : (
          <div className="flex flex-wrap gap-2">
            {Array.from(selectedOptions).map((option) => (
              <div
                key={option}
                className="w-8 h-8 rounded-md border-2 border-primary bg-primary/10"
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
      </div>

      {/* Selector Panel */}
      {isOpen && (
        <div className="bg-card border border-border rounded-md p-3 space-y-3">
          {/* Clear Button */}
          <div className="flex justify-between items-center">
            <span className="text-sm text-muted-foreground">
              {selectedCountText}
            </span>
            <Button variant="ghost" size="sm" onClick={clearAll}>
              {clearLabel}
            </Button>
          </div>

          {/* Options Grid */}
          <div className="flex flex-wrap gap-2">
            {options.map((option) => {
              const isSelected = selectedOptions.has(option)
              const label = getKeywordDisplayName(option)
              return (
                <button
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

          {/* Close Button */}
          <div className="flex justify-end">
            <Button variant="outline" size="sm" onClick={() => { setIsOpen(false); }}>
              {doneLabel}
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}

/**
 * Creates default equipment for all sinners
 * Note: skillEA is managed separately, not in equipment
 */
function createDefaultEquipment(): Record<string, SinnerEquipment> {
  const equipment: Record<string, SinnerEquipment> = {}
  SINNERS.forEach((sinner, index) => {
    const sinnerIdPart = (index + 1).toString().padStart(2, '0')
    const defaultIdentityId = `1${sinnerIdPart}01`
    const defaultEgoId = `2${sinnerIdPart}01`
    equipment[sinner] = {
      identity: { id: defaultIdentityId, uptie: 4, level: MAX_LEVEL },
      egos: {
        ZAYIN: { id: defaultEgoId, threadspin: 4 },
      },
    }
  })
  return equipment
}

/**
 * Creates default skill EA state for all sinners
 */
function createDefaultSkillEAState(): Record<string, SkillEAState> {
  const state: Record<string, SkillEAState> = {}
  for (const sinner of SINNERS) {
    state[sinner] = { ...DEFAULT_SKILL_EA }
  }
  return state
}

/**
 * Inner component with suspending data hooks
 */
function PlannerMDNewPageContent() {
  const { t } = useTranslation(['planner', 'common'])
  const { data: user } = useAuthQuery()
  const plannerStorage = usePlannerStorage()
  const config = usePlannerConfig()

  // State for draft recovery dialog
  const [showRecoveryDialog, setShowRecoveryDialog] = useState(false)
  const [recoveredDraft, setRecoveredDraft] = useState<SaveablePlanner | null>(null)

  // State for category selector (default: 5F)
  const [category, setCategory] = useState<MDCategory>('5F')

  // Progressive rendering: render sections one-by-one
  // Sections: Deck, StartBuff, StartGift, Observation, SkillReplacement, Comprehensive, Floors
  const [visibleSections, setVisibleSections] = useState(1)

  // Total sections: 6 fixed sections + floor sections (based on category)
  const floorCount = FLOOR_COUNTS[category]
  const totalSections = 6 + floorCount

  // Progressively show more sections
  useEffect(() => {
    if (visibleSections < totalSections) {
      const rafId = requestAnimationFrame(() => {
        setVisibleSections((prev) => prev + 1)
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [visibleSections, totalSections])

  // Reset visible sections when category changes (affects floor count)
  useEffect(() => {
    setVisibleSections(1)
  }, [category])

  // State for keyword multi-selector (default: empty)
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())

  // State for start buff selection
  const [selectedBuffIds, setSelectedBuffIds] = useState<Set<number>>(new Set())
  const [isStartBuffPaneOpen, setIsStartBuffPaneOpen] = useState(false)

  // State for start gift pane
  const [isStartGiftPaneOpen, setIsStartGiftPaneOpen] = useState(false)

  // State for observation gift pane
  const [isObservationPaneOpen, setIsObservationPaneOpen] = useState(false)

  // State for comprehensive gift pane
  const [isComprehensivePaneOpen, setIsComprehensivePaneOpen] = useState(false)

  // State for deck builder pane (lifted for filter persistence across open/close)
  const [isDeckPaneOpen, setIsDeckPaneOpen] = useState(false)
  const [deckFilterState, setDeckFilterState] = useState<DeckFilterState>({
    entityMode: 'identity',
    selectedSinners: new Set(),
    selectedKeywords: new Set(),
    searchQuery: '',
  })

  // State for deck import confirmation dialog
  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [pendingImport, setPendingImport] = useState<DecodedDeck | null>(null)

  // State for start gift selection
  const [selectedGiftKeyword, setSelectedGiftKeyword] = useState<string | null>(null)
  const [selectedGiftIds, setSelectedGiftIds] = useState<Set<string>>(new Set())

  // State for observation gift selection
  const [observationGiftIds, setObservationGiftIds] = useState<Set<string>>(new Set())

  // State for comprehensive gift selection (encoded: enhancement + giftId)
  const [comprehensiveGiftIds, setComprehensiveGiftIds] = useState<Set<string>>(new Set())

  // State for deck equipment (lifted for saving)
  const [equipment, setEquipment] = useState<Record<string, SinnerEquipment>>(createDefaultEquipment)
  const [deploymentOrder, setDeploymentOrder] = useState<number[]>([])

  // State for skill EA (independent from equipment's skillEA for section control)
  const [skillEAState, setSkillEAState] = useState<Record<string, SkillEAState>>(createDefaultSkillEAState)

  // State for title input
  const [title, setTitle] = useState<string>('')

  // State for floor theme selections (per-floor: themePackId, difficulty, giftIds)
  const [floorSelections, setFloorSelections] = useState<FloorThemeSelection[]>(() =>
    Array.from({ length: 15 }, () => ({
      themePackId: null,
      difficulty: DUNGEON_IDX.NORMAL,
      giftIds: new Set<string>(),
    }))
  )

  // State for note editor content per section
  // Keys: 'deckBuilder', 'startBuffs', 'startGifts', 'observation', 'skillReplacement', 'comprehensiveGifts', 'floor-0', 'floor-1', etc.
  const [sectionNotes, setSectionNotes] = useState<Record<string, NoteContent>>(() => {
    const notes: Record<string, NoteContent> = {
      deckBuilder: createEmptyNoteContent(),
      startBuffs: createEmptyNoteContent(),
      startGifts: createEmptyNoteContent(),
      observation: createEmptyNoteContent(),
      skillReplacement: createEmptyNoteContent(),
      comprehensiveGifts: createEmptyNoteContent(),
    }
    // Add floor notes
    for (let i = 0; i < 15; i++) {
      notes[`floor-${i}`] = createEmptyNoteContent()
    }
    return notes
  })

  // Handler for section note changes
  const handleSectionNoteChange = (sectionKey: string, content: NoteContent) => {
    setSectionNotes((prev) => ({
      ...prev,
      [sectionKey]: content,
    }))
  }

  // Compose planner state for autosave hook
  const plannerState: PlannerState = {
    title,
    category,
    selectedKeywords,
    selectedBuffIds,
    selectedGiftKeyword,
    selectedGiftIds,
    observationGiftIds,
    comprehensiveGiftIds,
    equipment,
    deploymentOrder,
    skillEAState,
    floorSelections,
    sectionNotes,
  }

  /**
   * Callback to reload state from server planner (on conflict discard)
   */
  const handleServerReload = (planner: SaveablePlanner) => {
    // Guard: MD planner page only handles MIRROR_DUNGEON type
    if (planner.config.type !== 'MIRROR_DUNGEON') {
      console.error('Attempted to reload non-MD planner in MD page:', planner.config.type)
      return
    }

    // Type-narrow: now safe after guard check
    const content = planner.content as MDPlannerContent
    const deserialized = deserializeSets(content)

    // Update published state from server
    setIsPublished(planner.metadata.published ?? false)

    setTitle(content.title)
    setCategory(planner.config.category as MDCategory)
    setSelectedKeywords(deserialized.selectedKeywords)
    setSelectedBuffIds(deserialized.selectedBuffIds)
    setSelectedGiftKeyword(content.selectedGiftKeyword)
    setSelectedGiftIds(deserialized.selectedGiftIds)
    setObservationGiftIds(deserialized.observationGiftIds)
    setComprehensiveGiftIds(deserialized.comprehensiveGiftIds)
    setEquipment(content.equipment)
    setDeploymentOrder(content.deploymentOrder)
    setSkillEAState(content.skillEAState)
    setFloorSelections(deserialized.floorSelections)
    // Convert SerializableNoteContent back to NoteContent
    const notes: Record<string, NoteContent> = {}
    for (const [key, note] of Object.entries(content.sectionNotes)) {
      notes[key] = { content: note.content }
    }
    setSectionNotes(notes)
  }

  // Unified save hook - handles auto-save and manual save
  const {
    plannerId,
    isAutoSaving,
    isSaving,
    errorCode,
    conflictState,
    clearError,
    save,
    resolveConflict,
  } = usePlannerSave({
    state: plannerState,
    schemaVersion: config.schemaVersion,
    contentVersion: config.mdCurrentVersion,
    plannerType: 'MIRROR_DUNGEON',
    onServerReload: handleServerReload,
  })

  // Display error toasts when errorCode changes
  useEffect(() => {
    if (!errorCode) return

    if (errorCode === 'saveFailed') {
      toast.error(t('pages.plannerMD.save.failed'))
      clearError()
    } else if (errorCode === 'quotaExceeded') {
      toast.error(t('pages.plannerMD.save.quotaExceeded', 'Storage quota exceeded'))
      clearError()
    }
    // 'conflict' is handled by the dialog, not a toast
  }, [errorCode, clearError, t])

  // Load identity and EGO spec data for deck import/export
  // Using spec-only hooks to avoid re-suspension on language changes
  const identitySpec = useIdentityListSpec()
  const egoSpec = useEGOListSpec()

  // Check for current draft on mount (runs once)
  useEffect(() => {
    const checkForDraft = async () => {
      const draft = await plannerStorage.loadCurrentDraft()
      // Only show recovery dialog for drafts, not saved planners
      if (draft?.metadata.status === 'draft') {
        setRecoveredDraft(draft)
        setShowRecoveryDialog(true)
      }
    }
    checkForDraft()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- plannerStorage functions are stable, only run on mount
  }, [])

  // Handler for continuing with the recovered draft
  const handleContinueDraft = () => {
    if (!recoveredDraft) {
      setShowRecoveryDialog(false)
      return
    }

    // Guard: MD planner page only handles MIRROR_DUNGEON type
    if (recoveredDraft.config.type !== 'MIRROR_DUNGEON') {
      console.error('Attempted to load non-MD planner in MD page:', recoveredDraft.config.type)
      setShowRecoveryDialog(false)
      return
    }

    // Type-narrow: now safe after guard check
    const content = recoveredDraft.content as MDPlannerContent

    // Deserialize Sets from arrays
    const deserialized = deserializeSets({
      selectedKeywords: content.selectedKeywords,
      selectedBuffIds: content.selectedBuffIds,
      selectedGiftIds: content.selectedGiftIds,
      observationGiftIds: content.observationGiftIds,
      comprehensiveGiftIds: content.comprehensiveGiftIds,
      floorSelections: content.floorSelections,
    })

    // Restore all state values
    setTitle(content.title)
    setCategory(recoveredDraft.config.category as MDCategory)
    setSelectedKeywords(deserialized.selectedKeywords)
    setSelectedBuffIds(deserialized.selectedBuffIds)
    setSelectedGiftKeyword(content.selectedGiftKeyword)
    setSelectedGiftIds(deserialized.selectedGiftIds)
    setObservationGiftIds(deserialized.observationGiftIds)
    setComprehensiveGiftIds(deserialized.comprehensiveGiftIds)
    setEquipment(content.equipment)
    setDeploymentOrder(content.deploymentOrder)
    setSkillEAState(content.skillEAState)

    // Restore floor selections (deserialized already has Sets for giftIds)
    setFloorSelections(deserialized.floorSelections as FloorThemeSelection[])

    // Restore section notes (SerializableNoteContent is compatible with NoteContent)
    const restoredNotes: Record<string, NoteContent> = {}
    for (const [key, note] of Object.entries(content.sectionNotes)) {
      restoredNotes[key] = { content: note.content }
    }
    setSectionNotes(restoredNotes)

    // Restore published state from metadata
    setIsPublished(recoveredDraft.metadata.published ?? false)

    // Close the dialog
    setShowRecoveryDialog(false)
  }

  // Handler for starting fresh (discard the draft)
  const handleStartFresh = async () => {
    // Clear the current draft ID so it won't be shown again
    await plannerStorage.setCurrentDraftId(null)
    setRecoveredDraft(null)
    setShowRecoveryDialog(false)
  }

  // Format date for display in recovery dialog
  const formatDraftDate = (isoDate: string): string => {
    const date = new Date(isoDate)
    return date.toLocaleString()
  }

  const titleByteLength = calculateByteLength(title)
  const isTitleValid = titleByteLength <= MAX_TITLE_BYTES

  // Handler for theme pack selection on a floor
  const handleFloorThemePackSelect = (floorIndex: number, packId: string, difficulty: DungeonIdx) => {
    setFloorSelections((prev) => {
      const next = [...prev]
      next[floorIndex] = {
        ...next[floorIndex],
        themePackId: packId,
        difficulty,
        // Clear gift selection when theme pack changes
        giftIds: new Set<string>(),
      }
      return next
    })
  }

  // Handler for gift selection on a floor
  const handleFloorGiftSelectionChange = (floorIndex: number, giftIds: Set<string>) => {
    setFloorSelections((prev) => {
      const next = [...prev]
      next[floorIndex] = {
        ...next[floorIndex],
        giftIds,
      }
      return next
    })
  }

  // Get previous floor's difficulty for constraint checking
  const getPreviousFloorDifficulty = (floorIndex: number): DungeonIdx | null => {
    if (floorIndex === 0) return null
    return floorSelections[floorIndex - 1].difficulty
  }

  // Generate floor indices array for rendering
  const floorIndices = useMemo(() =>
    Array.from({ length: floorCount }, (_, i) => i),
    [floorCount]
  )

  const handleTitleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setTitle(e.target.value)
  }

  // Deck builder handlers
  const handleToggleDeploy = (sinnerIndex: number) => {
    setDeploymentOrder((prev) => {
      const currentIndex = prev.indexOf(sinnerIndex)
      if (currentIndex >= 0) {
        const newOrder = [...prev]
        newOrder.splice(currentIndex, 1)
        return newOrder
      } else {
        return [...prev, sinnerIndex]
      }
    })
  }

  const handleResetDeployment = () => {
    setDeploymentOrder([])
  }

  const handleDeckExport = async () => {
    try {
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

  /**
   * Handler for manual save button
   * Validates floor theme packs before saving
   * Uses the unified save hook and shows success toast
   */
  const handleSave = async () => {
    // Validate floor theme pack selections before saving
    const validationErrors = validateFloorThemePacksForSave(floorSelections, floorCount)

    if (validationErrors.length > 0) {
      // Show first validation error as toast
      const firstError = validationErrors[0]
      toast.error(firstError.message)
      return
    }

    const success = await save()
    if (success) {
      toast.success(t('pages.plannerMD.save.success'))
    }
    // Errors are handled by the hook's error state and effect
  }

  /**
   * Handler for publish button
   * Validates floor theme packs, saves planner first, then toggles publish state
   */
  const [isPublishing, setIsPublishing] = useState(false)
  const [isPublished, setIsPublished] = useState(false)
  const handlePublish = async () => {
    setIsPublishing(true)
    try {
      // Validate floor theme pack selections before publishing
      const validationErrors = validateFloorThemePacksForSave(floorSelections, floorCount)

      if (validationErrors.length > 0) {
        // Show first validation error as toast
        const firstError = validationErrors[0]
        toast.error(firstError.message)
        return
      }

      const success = await save()
      if (!success) return

      const response = await plannerApi.togglePublish(plannerId)
      const wasPublished = isPublished
      setIsPublished(response.published)
      toast.success(t(wasPublished ? 'pages.plannerMD.publish.unpublishSuccess' : 'pages.plannerMD.publish.success'))
    } catch (error) {
      // Parse error response for specific messages
      let errorMessage = t('pages.plannerMD.publish.failed')
      if (error instanceof Error) {
        const errorText = error.message.toLowerCase()
        if (errorText.includes('403') || errorText.includes('forbidden')) {
          errorMessage = t('common.errors.forbidden')
        } else if (errorText.includes('404') || errorText.includes('not found')) {
          errorMessage = t('common.errors.notFound')
        } else if (errorText.includes('429') || errorText.includes('rate limit')) {
          errorMessage = t('common.errors.rateLimit')
        }
      }
      toast.error(errorMessage)
    } finally {
      setIsPublishing(false)
    }
  }

  return (
    <div className="container mx-auto p-8">
      {/* Draft Recovery Dialog */}
      <Dialog open={showRecoveryDialog} onOpenChange={setShowRecoveryDialog}>
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>{t('pages.plannerMD.draftRecovery.title')}</DialogTitle>
            <DialogDescription>
              {t('pages.plannerMD.draftRecovery.description')}
            </DialogDescription>
          </DialogHeader>
          {recoveredDraft && (
            <div className="py-2 text-sm text-muted-foreground">
              {t('pages.plannerMD.draftRecovery.draftInfo', {
                title: recoveredDraft.content.title || t('pages.plannerMD.draftRecovery.untitled'),
                date: formatDraftDate(recoveredDraft.metadata.lastModifiedAt),
              })}
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={handleStartFresh}>
              {t('pages.plannerMD.draftRecovery.startFresh')}
            </Button>
            <Button onClick={handleContinueDraft}>
              {t('pages.plannerMD.draftRecovery.continue')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Conflict Resolution Dialog */}
      <ConflictResolutionDialog
        open={errorCode === 'conflict'}
        conflictState={conflictState}
        onChoice={resolveConflict}
        isResolving={isSaving}
      />

      <div className="flex items-center justify-between mb-4">
        <h1 className="text-3xl font-bold">{t('pages.plannerMD.title')}</h1>
        <div className="flex items-center gap-2">
          {isAutoSaving && (
            <span className="text-sm text-muted-foreground">
              {t('pages.plannerMD.save.autoSaving', 'Saving...')}
            </span>
          )}
          <Button onClick={handleSave} disabled={isSaving || isPublishing} variant="outline">
            <Save className="w-4 h-4 mr-2" />
            {isSaving ? t('pages.plannerMD.save.saving') : t('pages.plannerMD.save.button')}
          </Button>
          {user && (
            <Button onClick={handlePublish} disabled={isSaving || isPublishing}>
              <Upload className="w-4 h-4 mr-2" />
              {isPublishing
                ? t(isPublished ? 'pages.plannerMD.publish.unpublishing' : 'pages.plannerMD.publish.publishing')
                : isPublished
                  ? t('pages.plannerMD.publish.unpublish')
                  : t('pages.plannerMD.publish.button')}
            </Button>
          )}
        </div>
      </div>
      <p className="text-muted-foreground mb-6">{t('pages.plannerMD.description')}</p>

      <div className="bg-background rounded-lg p-6 space-y-2">
        {/* Category Selector and Keyword Multi-Selector (horizontal layout) */}
        <div className="flex flex-col sm:flex-row gap-4 items-start">
          {/* Category Selector */}
          <div className="flex flex-col sm:flex-row sm:items-start gap-2 h-12">
            <label className="text-sm font-medium whitespace-nowrap sm:mt-2">{t('pages.plannerMD.category')}</label>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" className="w-24 h-10 justify-between">
                  {category}
                  <ChevronDown className="ml-2 h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start">
                {MD_CATEGORIES.map((cat) => (
                  <DropdownMenuItem key={cat} onClick={() => { setCategory(cat); }}>
                    {cat}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>

          {/* Keyword Multi-Selector */}
          <div className="flex flex-col sm:flex-row sm:items-start gap-2 w-full sm:w-auto">
            <label className="text-sm font-medium whitespace-nowrap sm:mt-2">{t('pages.plannerMD.keywords')}</label>
            <div className="w-full sm:w-80">
              <KeywordSelector
                options={PLANNER_KEYWORDS}
                selectedOptions={selectedKeywords}
                onSelectionChange={setSelectedKeywords}
                getIconPath={getPlannerKeywordIconPath}
                placeholder={t('pages.plannerMD.keywordsPlaceholder')}
                clearLabel={t('pages.plannerMD.clearKeywords')}
                selectedCountText={t('pages.plannerMD.keywordSelector.selected', { count: selectedKeywords.size })}
                doneLabel={t('pages.plannerMD.done')}
              />
            </div>
          </div>
        </div>

        {/* Title Input */}
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

        {/* Section 1: Deck Builder Summary + Pane */}
        {visibleSections >= 1 && (
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
              equipment={equipment}
              deploymentOrder={deploymentOrder}
              onToggleDeploy={handleToggleDeploy}
              onImport={handleDeckImport}
              onExport={handleDeckExport}
              onResetOrder={handleResetDeployment}
              onEditDeck={() => { startTransition(() => setIsDeckPaneOpen(true)) }}
            />
            <DeckBuilderPane
              open={isDeckPaneOpen}
              onOpenChange={setIsDeckPaneOpen}
              equipment={equipment}
              setEquipment={setEquipment}
              deploymentOrder={deploymentOrder}
              setDeploymentOrder={setDeploymentOrder}
              filterState={deckFilterState}
              setFilterState={setDeckFilterState}
              onImport={handleDeckImport}
              onExport={handleDeckExport}
              onResetOrder={handleResetDeployment}
            />
          </Suspense>
        )}

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

        {/* Section 2: Start Buff */}
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
              selectedBuffIds={selectedBuffIds}
              onSelectionChange={setSelectedBuffIds}
              onClick={() => { setIsStartBuffPaneOpen(true); }}
            />
            <StartBuffEditPane
              open={isStartBuffPaneOpen}
              onOpenChange={setIsStartBuffPaneOpen}
              mdVersion={config.mdCurrentVersion}
              selectedBuffIds={selectedBuffIds}
              onSelectionChange={setSelectedBuffIds}
            />
            <NoteEditor
              value={sectionNotes.startBuffs}
              onChange={(content) => { handleSectionNoteChange('startBuffs', content); }}
              placeholder={t('pages.plannerMD.noteEditor.placeholder')}
              maxBytes={MAX_NOTE_BYTES}
            />
          </Suspense>
        )}

        {/* Section 3: Start Gift */}
        {visibleSections >= 3 && (
          <Suspense
            fallback={
              <div className="space-y-2">
                <Skeleton className="h-32 w-full rounded-lg" />
              </div>
            }
          >
            <StartGiftSummary
              selectedKeyword={selectedGiftKeyword}
              selectedGiftIds={selectedGiftIds}
              onClick={() => { setIsStartGiftPaneOpen(true); }}
            />
            <StartGiftEditPane
              open={isStartGiftPaneOpen}
              onOpenChange={setIsStartGiftPaneOpen}
              mdVersion={config.mdCurrentVersion}
              selectedBuffIds={selectedBuffIds}
              selectedKeyword={selectedGiftKeyword}
              selectedGiftIds={selectedGiftIds}
              onKeywordChange={setSelectedGiftKeyword}
              onGiftSelectionChange={setSelectedGiftIds}
            />
            <NoteEditor
              value={sectionNotes.startGifts}
              onChange={(content) => { handleSectionNoteChange('startGifts', content); }}
              placeholder={t('pages.plannerMD.noteEditor.placeholder')}
              maxBytes={MAX_NOTE_BYTES}
            />
          </Suspense>
        )}

        {/* Section 4: EGO Gift Observation */}
        {visibleSections >= 4 && (
          <>
            <Suspense
              fallback={
                <PlannerSection title={t('pages.plannerMD.egoGiftObservation')}>
                  <div className="space-y-4">
                    {/* Cost display skeleton */}
                    <div className="flex justify-end">
                      <div className="flex items-center gap-1">
                        <Skeleton className="w-8 h-8 rounded-md" />
                        <Skeleton className="w-12 h-6" />
                      </div>
                    </div>
                    {/* Gift cards skeleton */}
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
                selectedGiftIds={observationGiftIds}
                onClick={() => { setIsObservationPaneOpen(true); }}
              />
            </Suspense>
            <Suspense fallback={null}>
              <EGOGiftObservationEditPane
                open={isObservationPaneOpen}
                onOpenChange={setIsObservationPaneOpen}
                selectedGiftIds={observationGiftIds}
                onSelectionChange={setObservationGiftIds}
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

        {/* Section 5: Skill Replacement */}
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
              <SkillReplacementSection
                equipment={equipment}
                skillEAState={skillEAState}
                setSkillEAState={setSkillEAState}
              />
            </Suspense>
            <NoteEditor
              value={sectionNotes.skillReplacement}
              onChange={(content) => { handleSectionNoteChange('skillReplacement', content); }}
              placeholder={t('pages.plannerMD.noteEditor.placeholder')}
              maxBytes={MAX_NOTE_BYTES}
            />
          </>
        )}

        {/* Section 6: EGO Gift Comprehensive List */}
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
                selectedGiftIds={comprehensiveGiftIds}
                onClick={() => setIsComprehensivePaneOpen(true)}
              />
            </Suspense>
            <Suspense fallback={null}>
              <ComprehensiveGiftSelectorPane
                open={isComprehensivePaneOpen}
                onOpenChange={setIsComprehensivePaneOpen}
                selectedGiftIds={comprehensiveGiftIds}
                onGiftSelectionChange={setComprehensiveGiftIds}
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

        {/* Sections 7+: Floor Theme and Gift (one section per floor) */}
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
                  // Section 7 = floor 0, Section 8 = floor 1, etc.
                  const sectionIndex = 7 + floorIndex
                  if (visibleSections < sectionIndex) return null

                  const floorNumber = floorIndex + 1
                  const selection = floorSelections[floorIndex]
                  const floorNoteKey = `floor-${floorIndex}`
                  return (
                    <div key={floorIndex} className="space-y-2">
                      <FloorThemeGiftSection
                        floorNumber={floorNumber}
                        floorIndex={floorIndex}
                        floorSelections={floorSelections}
                        previousFloorDifficulty={getPreviousFloorDifficulty(floorIndex)}
                        selectedThemePackId={selection.themePackId}
                        selectedDifficulty={selection.difficulty}
                        selectedGiftIds={selection.giftIds}
                        onThemePackSelect={(packId, difficulty) =>
                          { handleFloorThemePackSelect(floorIndex, packId, difficulty); }
                        }
                        setSelectedGiftIds={(giftIds) =>
                          { handleFloorGiftSelectionChange(floorIndex, giftIds); }
                        }
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

        {/* Bottom Action Buttons */}
        <div className="flex justify-end gap-2 pt-6 border-t">
          <Button onClick={handleSave} disabled={isSaving || isPublishing} variant="outline">
            <Save className="w-4 h-4 mr-2" />
            {isSaving ? t('pages.plannerMD.save.saving') : t('pages.plannerMD.save.button')}
          </Button>
          {user && (
            <Button onClick={handlePublish} disabled={isSaving || isPublishing}>
              <Upload className="w-4 h-4 mr-2" />
              {isPublishing
                ? t(isPublished ? 'pages.plannerMD.publish.unpublishing' : 'pages.plannerMD.publish.publishing')
                : isPublished
                  ? t('pages.plannerMD.publish.unpublish')
                  : t('pages.plannerMD.publish.button')}
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}

/**
 * Page-level skeleton for initial data load
 */
function PlannerMDNewPageSkeleton() {
  return (
    <div className="container mx-auto p-8">
      {/* Header skeleton */}
      <div className="flex items-center justify-between mb-4">
        <div className="space-y-2">
          <Skeleton className="h-9 w-64" /> {/* Title */}
        </div>
        <Skeleton className="h-10 w-24" /> {/* Save button */}
      </div>
      <Skeleton className="h-5 w-96 mb-6" /> {/* Description */}

      {/* Main content skeleton */}
      <div className="bg-background rounded-lg p-6 space-y-6">
        {/* Category and keywords */}
        <div className="flex gap-4">
          <Skeleton className="h-10 w-32" />
          <Skeleton className="h-10 flex-1" />
        </div>

        {/* Title input */}
        <Skeleton className="h-10 w-full" />

        {/* Deck builder skeleton */}
        <div className="space-y-2">
          <Skeleton className="h-6 w-32" />
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

        {/* Additional sections */}
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="space-y-2">
            <Skeleton className="h-6 w-48" />
            <Skeleton className="h-32 w-full rounded-lg" />
          </div>
        ))}
      </div>
    </div>
  )
}

/**
 * Main export with Suspense boundary
 * Skeleton blocks page until data loads, then progressive rendering takes over
 */
export default function PlannerMDNewPage() {
  return (
    <Suspense fallback={<PlannerMDNewPageSkeleton />}>
      <PlannerMDNewPageContent />
    </Suspense>
  )
}
