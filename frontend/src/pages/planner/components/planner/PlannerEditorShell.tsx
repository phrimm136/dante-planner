import { useState, useEffect, useMemo, Suspense, startTransition } from 'react'

import { useNavigate } from '@tanstack/react-router'
import { queryClient } from '@/lib/queryClient'
import { plannerQueryKeys } from '../../lib/plannerQueryKeys'
import { publishedPlannerQueryKeys } from '../../hooks/usePublishedPlannerQuery'

import { useTranslation } from 'react-i18next'
import { ChevronDown, Save } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

import {
  MD_CATEGORIES,
  PLANNER_KEYWORDS,
  DEFAULT_SKILL_EA,
  allowedDifficulties,
  floorCount as categoryFloorCount,
} from '@/shared/gameData'
import { SECTION_STYLES } from '@/lib/constants'
import { getKeywordIconPath } from '@/shared/assets'
import { assertNever, calculateByteLength } from '@/lib/utils'
import { CONFLICT_TOAST_KEY } from '../../lib/conflictChoice'
import { MdCategoryLabel } from '../MdCategoryLabel'
import { showAppError, showErrorMessage, showSuccess, showWarning } from '@/lib/errorPresentation'
import { isSyncConflict } from '@/lib/apiErrorClassifier'

import type { MDCategory } from '@/shared/gameData'
import { isMDPlanner } from '../../types/PlannerTypes'
import type { SaveablePlanner, ConflictResolutionChoice } from '../../types/PlannerTypes'

import { usePlannerEditorStore, usePlannerEditorStoreApi } from '../../stores/usePlannerEditorStore'

import { useDeckClipboard } from '../../hooks/useDeckClipboard'
import { usePlannerSave } from '../../hooks/usePlannerSave'
import type { SaveOptions } from '../../hooks/usePlannerSave'
import { usePlannerConfig } from '../../hooks/usePlannerConfig'
import { useUserSettingsQuery } from '@/shared/userSettings'

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
  SkillGridSkeleton,
  StartBuffSkeleton,
  StartGiftSkeleton,
} from '../plannerSkeletons'
import { DeckImportConfirmDialog } from '../deckBuilder/DeckImportConfirmDialog'
import { StoreBoundSectionNote } from './StoreBoundSectionNote'
import { ConflictResolutionDialog } from './ConflictResolutionDialog'
import { SyncOffWarningDialog } from '../SyncOffWarningDialog'
import { KeywordSelector } from './KeywordSelector'
import { LastSavedLabel } from './LastSavedLabel'

const MAX_TITLE_BYTES = 256

export interface PlannerEditorSession {
  contentVersion: number
  initialPlannerId?: string | undefined
  initialSyncVersion?: number | undefined
  initialSavedAt?: string | undefined
}

export function PlannerEditorShell({
  contentVersion: mdVersion,
  initialPlannerId,
  initialSyncVersion,
  initialSavedAt,
}: PlannerEditorSession) {
  const { t } = useTranslation(['planner', 'common'])

  const config = usePlannerConfig()
  const navigate = useNavigate()

  const { data: userSettings } = useUserSettingsQuery()
  const syncEnabled = userSettings?.syncEnabled ?? false

  const handleKeepBothCreated = (newPlannerId: string) => {
    void navigate({ to: '/planner/md/$id/edit', params: { id: newPlannerId }, replace: true })
  }

  const storeApi = usePlannerEditorStoreApi()

  const title = usePlannerEditorStore((s) => s.title)
  const setTitle = usePlannerEditorStore((s) => s.setTitle)
  const category = usePlannerEditorStore((s) => s.category)
  const setCategory = usePlannerEditorStore((s) => s.setCategory)
  const isPublished = usePlannerEditorStore((s) => s.isPublished)
  const visibleSections = usePlannerEditorStore((s) => s.visibleSections)
  const setVisibleSections = usePlannerEditorStore((s) => s.setVisibleSections)
  const selectedKeywords = usePlannerEditorStore((s) => s.selectedKeywords)
  const setSelectedKeywords = usePlannerEditorStore((s) => s.setSelectedKeywords)

  const setEquipment = usePlannerEditorStore((s) => s.setEquipment)
  const setDeploymentOrder = usePlannerEditorStore((s) => s.setDeploymentOrder)
  const updateSinnerSkillEA = usePlannerEditorStore((s) => s.updateSinnerSkillEA)
  const initializeFromPlannerAction = usePlannerEditorStore((s) => s.initializeFromPlanner)

  const [isStartBuffPaneOpen, setIsStartBuffPaneOpen] = useState(false)
  const [isStartGiftPaneOpen, setIsStartGiftPaneOpen] = useState(false)
  const [isObservationPaneOpen, setIsObservationPaneOpen] = useState(false)
  const [isComprehensivePaneOpen, setIsComprehensivePaneOpen] = useState(false)
  const [isDeckPaneOpen, setIsDeckPaneOpen] = useState(false)
  const [showSaveWarning, setShowSaveWarning] = useState(false)

  const floorCount = categoryFloorCount(category)

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

  const handleCategoryChange = (newCategory: MDCategory) => {
    const currentCategory = storeApi.getState().category
    const floorSelections = storeApi.getState().floorSelections
    const sharedFloorCount = Math.min(
      categoryFloorCount(currentCategory),
      categoryFloorCount(newCategory),
    )

    const hasDisallowedDifficulty = floorSelections
      .slice(0, sharedFloorCount)
      .some(
        (floor, floorIndex) =>
          floor.themePackId !== null &&
          !(allowedDifficulties(newCategory, floorIndex) ?? []).includes(floor.difficulty),
      )

    if (hasDisallowedDifficulty) {
      showWarning('planner:pages.plannerMD.publish.requiresHardMode')
    }

    setCategory(newCategory)
  }

  const getState = () => storeApi.getState().getPlannerState()

  const {
    plannerId,
    isSaving,
    error: saveError,
    resolutionError,
    clearError,
    save,
    resolveConflict,
    saveStatus,
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

  const conflictState = useMemo(
    () =>
      isSyncConflict(saveError)
        ? { serverVersion: saveError.serverVersion, detectedAt: new Date().toISOString() }
        : null,
    [saveError],
  )

  useEffect(() => {
    if (!saveError) return

    if (isSyncConflict(saveError)) return

    showAppError(saveError)
    clearError()
  }, [saveError, clearError])

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
    const success = await save(saveOptions)
    if (!success) return

    showSuccess('planner:pages.plannerMD.save.success')
    navigateToViewer()
  }

  const handleSave = async () => {
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
    const success = await resolveConflict(choice)
    if (!success) return

    showSuccess(CONFLICT_TOAST_KEY[choice])

    switch (choice) {
      case 'overwrite':
      case 'discard':
        navigateToViewer()
        break
      case 'both':
        break
      default:
        assertNever(choice)
    }
  }

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
        <Suspense fallback={<StartBuffSkeleton />}>
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
        <Suspense fallback={<StartGiftSkeleton />}>
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
          <Suspense fallback={<GiftGridSkeleton title={t('pages.plannerMD.egoGiftObservation')} />}>
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

  const floorSlotStart = regularSectionCount + 1

  const sections: RevealSectionSpec[] = [
    ...regularSections,
    {
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

  useEffect(() => {
    if (visibleSections < totalSections) {
      const rafId = requestAnimationFrame(() => {
        setVisibleSections(visibleSections + 1)
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [visibleSections, totalSections, setVisibleSections])

  useEffect(() => {
    const newTotalSections = regularSectionCount + categoryFloorCount(category)
    if (visibleSections > newTotalSections) {
      setVisibleSections(newTotalSections)
    }
  }, [category, visibleSections, setVisibleSections, regularSectionCount])

  return (
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
        <LastSavedLabel status={saveStatus} />
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
  )
}
