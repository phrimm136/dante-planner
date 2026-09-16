// React core
import { Suspense, useId, useState } from 'react'

// Third-party libraries
import { showSuccess } from '@/lib/errorPresentation'

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
import { DeckBuilderPageSkeleton } from './components/plannerSkeletons'
import { SECTION_STYLES } from '@/lib/constants'

/**
 * Inner content component with store access and handlers.
 * Uses Summary + Pane pattern: SinnerGrid viewer + Edit dialog.
 */
function DeckBuilderPageContent() {
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
    showSuccess('planner:deckBuilder.importSuccess')
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
