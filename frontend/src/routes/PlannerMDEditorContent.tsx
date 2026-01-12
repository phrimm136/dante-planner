// React core
import { useState, useEffect, Suspense, startTransition } from 'react'

// Third-party libraries
import { useTranslation } from 'react-i18next'
import { ChevronDown, Save, Upload } from 'lucide-react'
import { toast } from 'sonner'

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
import { MD_CATEGORIES, PLANNER_KEYWORDS, SINNERS, MAX_LEVEL, DEFAULT_SKILL_EA, FLOOR_COUNTS, DUNGEON_IDX, MAX_NOTE_BYTES } from '@/lib/constants'
import { getKeywordIconPath } from '@/lib/assetPaths'
import { getKeywordDisplayName, calculateByteLength } from '@/lib/utils'
import { validateFloorThemePacksForSave } from '@/lib/plannerHelpers'
import { encodeDeckCode, decodeDeckCode, validateDeckCode } from '@/lib/deckCode'
import { plannerApi } from '@/lib/plannerApi'

// Project types & schemas
import type { MDCategory, DungeonIdx } from '@/lib/constants'
import type { SinnerEquipment, SkillEAState, DeckFilterState } from '@/types/DeckTypes'
import type { FloorThemeSelection } from '@/types/ThemePackTypes'
import type { NoteContent } from '@/types/NoteEditorTypes'
import type { SaveablePlanner, MDPlannerContent } from '@/types/PlannerTypes'
import type { DecodedDeck } from '@/lib/deckCode'
import type { PlannerState } from '@/hooks/usePlannerSave'
import { createEmptyNoteContent } from '@/schemas/NoteEditorSchemas'
import { deserializeSets } from '@/schemas/PlannerSchemas'

// Project hooks
import { useIdentityListSpec } from '@/hooks/useIdentityListData'
import { useEGOListSpec } from '@/hooks/useEGOListData'
import { usePlannerStorage } from '@/hooks/usePlannerStorage'
import { usePlannerSave } from '@/hooks/usePlannerSave'
import { usePlannerConfig } from '@/hooks/usePlannerConfig'
import { useAuthQuery } from '@/hooks/useAuthQuery'

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
import { SectionNoteDialog } from '@/components/common/SectionNoteDialog'
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

      {isOpen && (
        <div className="bg-card border border-border rounded-md p-3 space-y-3">
          <div className="flex justify-between items-center">
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

function createDefaultSkillEAState(): Record<string, SkillEAState> {
  const state: Record<string, SkillEAState> = {}
  for (const sinner of SINNERS) {
    state[sinner] = { ...DEFAULT_SKILL_EA }
  }
  return state
}

export function PlannerMDEditorContent({ mode, planner }: PlannerMDEditorContentProps) {
  const { t } = useTranslation(['planner', 'common'])
  const { data: user } = useAuthQuery()
  const plannerStorage = usePlannerStorage()
  const config = usePlannerConfig()

  const [showRecoveryDialog, setShowRecoveryDialog] = useState(false)
  const [recoveredDraft, setRecoveredDraft] = useState<SaveablePlanner | null>(null)
  const [hasCheckedForDraft, setHasCheckedForDraft] = useState(false)

  const [category, setCategory] = useState<MDCategory>('5F')

  const [visibleSections, setVisibleSections] = useState(1)

  const floorCount = FLOOR_COUNTS[category]
  const totalSections = 6 + floorCount

  useEffect(() => {
    if (visibleSections < totalSections) {
      const rafId = requestAnimationFrame(() => {
        setVisibleSections((prev) => prev + 1)
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [visibleSections, totalSections])

  useEffect(() => {
    const newTotalSections = 6 + FLOOR_COUNTS[category]
    if (visibleSections > newTotalSections) {
      setVisibleSections(newTotalSections)
    }
  }, [category, visibleSections])

  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())

  const [selectedBuffIds, setSelectedBuffIds] = useState<Set<number>>(new Set())
  const [isStartBuffPaneOpen, setIsStartBuffPaneOpen] = useState(false)

  const [isStartGiftPaneOpen, setIsStartGiftPaneOpen] = useState(false)

  const [isObservationPaneOpen, setIsObservationPaneOpen] = useState(false)

  const [isComprehensivePaneOpen, setIsComprehensivePaneOpen] = useState(false)

  const [isDeckPaneOpen, setIsDeckPaneOpen] = useState(false)
  const [deckFilterState, setDeckFilterState] = useState<DeckFilterState>({
    entityMode: 'identity',
    selectedSinners: new Set(),
    selectedKeywords: new Set(),
    searchQuery: '',
  })

  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [pendingImport, setPendingImport] = useState<DecodedDeck | null>(null)

  const [selectedGiftKeyword, setSelectedGiftKeyword] = useState<string | null>(null)
  const [selectedGiftIds, setSelectedGiftIds] = useState<Set<string>>(new Set())

  const [observationGiftIds, setObservationGiftIds] = useState<Set<string>>(new Set())

  const [comprehensiveGiftIds, setComprehensiveGiftIds] = useState<Set<string>>(new Set())

  const [equipment, setEquipment] = useState<Record<string, SinnerEquipment>>(createDefaultEquipment)
  const [deploymentOrder, setDeploymentOrder] = useState<number[]>([])

  const [skillEAState, setSkillEAState] = useState<Record<string, SkillEAState>>(createDefaultSkillEAState)

  const [title, setTitle] = useState<string>('')

  const [floorSelections, setFloorSelections] = useState<FloorThemeSelection[]>(() =>
    Array.from({ length: 15 }, () => ({
      themePackId: null,
      difficulty: DUNGEON_IDX.NORMAL,
      giftIds: new Set<string>(),
    }))
  )

  const [sectionNotes, setSectionNotes] = useState<Record<string, NoteContent>>(() => {
    const notes: Record<string, NoteContent> = {
      deckBuilder: createEmptyNoteContent(),
      startBuffs: createEmptyNoteContent(),
      startGifts: createEmptyNoteContent(),
      observation: createEmptyNoteContent(),
      skillReplacement: createEmptyNoteContent(),
      comprehensiveGifts: createEmptyNoteContent(),
    }
    for (let i = 0; i < 15; i++) {
      notes[`floor-${i}`] = createEmptyNoteContent()
    }
    return notes
  })

  const handleSectionNoteChange = (sectionKey: string, content: NoteContent) => {
    setSectionNotes((prev) => ({
      ...prev,
      [sectionKey]: content,
    }))
  }

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

  const initializeFromPlanner = (loadedPlanner: SaveablePlanner) => {
    if (loadedPlanner.config.type !== 'MIRROR_DUNGEON') {
      console.error('Attempted to load non-MD planner in MD editor:', loadedPlanner.config.type)
      toast.error(t('pages.plannerMD.errors.invalidType', 'Cannot load: Invalid planner type'))
      return false
    }

    const content = loadedPlanner.content as MDPlannerContent
    const deserialized = deserializeSets(content)

    setIsPublished(loadedPlanner.metadata.published ?? false)
    setTitle(content.title)
    setCategory(loadedPlanner.config.category as MDCategory)
    setSelectedKeywords(deserialized.selectedKeywords)
    setSelectedBuffIds(deserialized.selectedBuffIds)
    setSelectedGiftKeyword(content.selectedGiftKeyword)
    setSelectedGiftIds(deserialized.selectedGiftIds)
    setObservationGiftIds(deserialized.observationGiftIds)
    setComprehensiveGiftIds(deserialized.comprehensiveGiftIds)
    setEquipment(content.equipment)
    setDeploymentOrder(content.deploymentOrder)
    setSkillEAState(content.skillEAState)
    setFloorSelections(deserialized.floorSelections as FloorThemeSelection[])

    const restoredNotes: Record<string, NoteContent> = {}
    for (const [key, note] of Object.entries(content.sectionNotes)) {
      restoredNotes[key] = { content: note.content }
    }
    setSectionNotes(restoredNotes)

    return true
  }

  const handleServerReload = (planner: SaveablePlanner) => {
    initializeFromPlanner(planner)
  }

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
    initialPlannerId: mode === 'edit' && planner?.metadata.id ? planner.metadata.id : undefined,
    initialSyncVersion: mode === 'edit' && planner?.metadata.syncVersion !== undefined ? planner.metadata.syncVersion : undefined,
    onServerReload: handleServerReload,
  })

  useEffect(() => {
    if (!errorCode) return

    if (errorCode === 'saveFailed') {
      toast.error(t('pages.plannerMD.save.failed'))
      clearError()
    } else if (errorCode === 'quotaExceeded') {
      toast.error(t('pages.plannerMD.save.quotaExceeded', 'Storage quota exceeded'))
      clearError()
    }
  }, [errorCode, clearError, t])

  const identitySpec = useIdentityListSpec()
  const egoSpec = useEGOListSpec()

  // Check for draft on mount (only once in 'new' mode)
  // plannerStorage excluded from deps: it's a new object every render but functions are stable
  // hasCheckedForDraft guard ensures this runs exactly once
  useEffect(() => {
    if (mode !== 'new' || hasCheckedForDraft) return

    const checkForDraft = async () => {
      const draft = await plannerStorage.loadCurrentDraft()
      if (draft?.metadata.status === 'draft') {
        setRecoveredDraft(draft)
        setShowRecoveryDialog(true)
      }
      setHasCheckedForDraft(true)
    }
    checkForDraft()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, hasCheckedForDraft])

  useEffect(() => {
    if (mode !== 'edit' || !planner) return
    initializeFromPlanner(planner)
  }, [mode, planner?.metadata.syncVersion])

  const handleContinueDraft = () => {
    if (!recoveredDraft) {
      setShowRecoveryDialog(false)
      return
    }

    if (recoveredDraft.config.type !== 'MIRROR_DUNGEON') {
      console.error('Attempted to load non-MD planner in MD page:', recoveredDraft.config.type)
      setShowRecoveryDialog(false)
      return
    }

    const content = recoveredDraft.content as MDPlannerContent

    const deserialized = deserializeSets({
      selectedKeywords: content.selectedKeywords,
      selectedBuffIds: content.selectedBuffIds,
      selectedGiftIds: content.selectedGiftIds,
      observationGiftIds: content.observationGiftIds,
      comprehensiveGiftIds: content.comprehensiveGiftIds,
      floorSelections: content.floorSelections,
    })

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

    setFloorSelections(deserialized.floorSelections as FloorThemeSelection[])

    const restoredNotes: Record<string, NoteContent> = {}
    for (const [key, note] of Object.entries(content.sectionNotes)) {
      restoredNotes[key] = { content: note.content }
    }
    setSectionNotes(restoredNotes)

    setIsPublished(recoveredDraft.metadata.published ?? false)

    setShowRecoveryDialog(false)
  }

  const handleStartFresh = async () => {
    await plannerStorage.setCurrentDraftId(null)
    setRecoveredDraft(null)
    setShowRecoveryDialog(false)
  }

  const formatDraftDate = (isoDate: string): string => {
    const date = new Date(isoDate)
    return date.toLocaleString()
  }

  const titleByteLength = calculateByteLength(title)
  const isTitleValid = titleByteLength <= MAX_TITLE_BYTES

  const handleFloorThemePackSelect = (floorIndex: number, packId: string, difficulty: DungeonIdx) => {
    setFloorSelections((prev) => {
      const next = [...prev]
      next[floorIndex] = {
        ...next[floorIndex],
        themePackId: packId,
        difficulty,
        giftIds: new Set<string>(),
      }
      return next
    })
  }

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

  const getPreviousFloorDifficulty = (floorIndex: number): DungeonIdx | null => {
    if (floorIndex === 0) return null
    return floorSelections[floorIndex - 1].difficulty
  }

  const floorIndices = Array.from({ length: floorCount }, (_, i) => i)

  const handleTitleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setTitle(e.target.value)
  }

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

  const handleSave = async () => {
    const validationErrors = validateFloorThemePacksForSave(floorSelections, floorCount)

    if (validationErrors.length > 0) {
      const firstError = validationErrors[0]
      toast.error(firstError.message)
      return
    }

    const success = await save()
    if (success) {
      toast.success(t('pages.plannerMD.save.success'))
    }
  }

  const [isPublishing, setIsPublishing] = useState(false)
  const [isPublished, setIsPublished] = useState(false)
  const handlePublish = async () => {
    setIsPublishing(true)
    try {
      const validationErrors = validateFloorThemePacksForSave(floorSelections, floorCount)

      if (validationErrors.length > 0) {
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
      <Dialog open={showRecoveryDialog && mode === 'new'} onOpenChange={setShowRecoveryDialog}>
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
        <div className="flex flex-col sm:flex-row gap-4 items-start">
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
                doneLabel={t('pages.plannerMD.done')}
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
                plannedEAState={skillEAState}
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
