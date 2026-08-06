// React core
import { Suspense, useId, useState } from 'react'

// Third-party libraries
import { useTranslation } from 'react-i18next'
import { toast } from '@/lib/toast'

// shadcn/ui components
import { Skeleton } from '@/components/ui/skeleton'

// Project constants
import { DEFAULT_SKILL_EA } from '@/shared/gameData'

// Store
import {
  PlannerEditorStoreProvider,
  usePlannerEditorStore,
  usePlannerEditorStoreApi,
} from './stores/usePlannerEditorStore'

// Project hooks
import { useDeckClipboard } from './hooks/useDeckClipboard'

// Project components (@/components)
import { StoreBoundDeckBuilderSummary } from './components/deckBuilder/DeckBuilderSummary'
import { DeckBuilderPane } from './components/deckBuilder/DeckBuilderPane'
import { StoreBoundDeckBuilderContent } from './components/deckBuilder/DeckBuilderContent'
import { DeckImportConfirmDialog } from './components/deckBuilder/DeckImportConfirmDialog'
import { staggerDelay } from '@/lib/stagger'
import { SECTION_STYLES } from '@/lib/constants'

/** Sinner card dimensions for skeleton (matches SinnerGrid) */
const SINNER_CARD = { width: 96, height: 128 }

/**
 * Page-level skeleton matching DeckBuilderSummary structure
 */
function DeckBuilderPageSkeleton() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <div className="space-y-4">
        {/* Section header */}
        <Skeleton className="h-8 w-40" />

        {/* SinnerGrid placeholder */}
        <div className="border-2 border-border rounded-lg p-4">
          <div className={SECTION_STYLES.LAYOUT.wrap}>
            {Array.from({ length: 12 }).map((_, i) => (
              <Skeleton
                key={i}
                className="rounded-md"
                style={{
                  width: SINNER_CARD.width,
                  height: SINNER_CARD.height,
                  ...staggerDelay(i),
                }}
              />
            ))}
          </div>

          {/* Status + Action bar placeholder */}
          <div className="mt-3 flex flex-col lg:flex-row lg:items-start lg:justify-between gap-3">
            <Skeleton className="h-20 w-full lg:w-96" />
            <div className="flex gap-2">
              <Skeleton className="h-10 w-24" />
              <Skeleton className="h-10 w-24" />
              <Skeleton className="h-10 w-24" />
              <Skeleton className="h-10 w-24" />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

/**
 * Inner content component with store access and handlers.
 * Uses Summary + Pane pattern: SinnerGrid viewer + Edit dialog.
 */
function DeckBuilderPageContent() {
  const { t } = useTranslation(['planner', 'common'])
  const storeApi = usePlannerEditorStoreApi()

  // Store actions
  const setEquipment = usePlannerEditorStore((s) => s.setEquipment)
  const setDeploymentOrder = usePlannerEditorStore((s) => s.setDeploymentOrder)
  const deploymentOrder = usePlannerEditorStore((s) => s.deploymentOrder)
  const updateSinnerSkillEA = usePlannerEditorStore((s) => s.updateSinnerSkillEA)

  // Pane (edit dialog) state
  const [isDeckPaneOpen, setIsDeckPaneOpen] = useState(false)

  const { handleImport, handleExport, pendingImport, clearPending } = useDeckClipboard({
    readDeck: () => storeApi.getState(),
  })

  const handleImportConfirm = () => {
    if (!pendingImport) return

    setEquipment(pendingImport.equipment)
    setDeploymentOrder(pendingImport.deploymentOrder)

    clearPending()
    toast.success(t('deckBuilder.importSuccess'))
  }

  const handleResetOrder = () => {
    setDeploymentOrder([])
  }

  const handleToggleDeploy = (sinnerIndex: number) => {
    const currentIndex = deploymentOrder.indexOf(sinnerIndex)
    if (currentIndex >= 0) {
      const newOrder = [...deploymentOrder]
      newOrder.splice(currentIndex, 1)
      setDeploymentOrder(newOrder)
    } else {
      setDeploymentOrder([...deploymentOrder, sinnerIndex])
    }
  }

  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      {/* Summary view: SinnerGrid + StatusViewer + ActionBar */}
      <StoreBoundDeckBuilderSummary
        onToggleDeploy={handleToggleDeploy}
        onImport={handleImport}
        onExport={handleExport}
        onResetOrder={handleResetOrder}
        onEditDeck={() => setIsDeckPaneOpen(true)}
      />

      {/* Edit dialog: full card selection grid */}
      <DeckBuilderPane open={isDeckPaneOpen} onOpenChange={setIsDeckPaneOpen}>
        <StoreBoundDeckBuilderContent
          isActive={isDeckPaneOpen}
          onImport={handleImport}
          onExport={handleExport}
          onResetOrder={handleResetOrder}
          onIdentityChange={(sinnerCode) => {
            updateSinnerSkillEA(sinnerCode, { ...DEFAULT_SKILL_EA })
          }}
        />
      </DeckBuilderPane>

      <DeckImportConfirmDialog
        pendingImport={pendingImport}
        onConfirm={handleImportConfirm}
        onCancel={clearPending}
      />
    </div>
  )
}

/**
 * Standalone deck builder page with ephemeral state.
 * Uses Summary + Pane pattern: rich SinnerGrid viewer with Edit dialog.
 * State resets on navigation (fresh store per mount).
 */
export default function DeckBuilderPage() {
  const storeKey = useId()

  return (
    <PlannerEditorStoreProvider key={storeKey}>
      <Suspense fallback={<DeckBuilderPageSkeleton />}>
        <DeckBuilderPageContent />
      </Suspense>
    </PlannerEditorStoreProvider>
  )
}
