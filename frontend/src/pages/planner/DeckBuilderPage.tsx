import { Suspense, useId, useState } from 'react'

import { showSuccess } from '@/lib/errorPresentation'

import { DEFAULT_SKILL_EA } from '@/shared/gameData'

import {
  PlannerEditorStoreProvider,
  usePlannerEditorStore,
  usePlannerEditorStoreApi,
} from './stores/usePlannerEditorStore'

import { useDeckClipboard } from './hooks/useDeckClipboard'

import { StoreBoundDeckBuilderSummary } from './components/deckBuilder/DeckBuilderSummary'
import { DeckBuilderPane } from './components/deckBuilder/DeckBuilderPane'
import { StoreBoundDeckBuilderContent } from './components/deckBuilder/DeckBuilderContent'
import { DeckImportConfirmDialog } from './components/deckBuilder/DeckImportConfirmDialog'
import { DeckBuilderPageSkeleton } from './components/plannerSkeletons'
import { SECTION_STYLES } from '@/lib/constants'

function DeckBuilderPageContent() {
  const storeApi = usePlannerEditorStoreApi()

  const setEquipment = usePlannerEditorStore((s) => s.setEquipment)
  const setDeploymentOrder = usePlannerEditorStore((s) => s.setDeploymentOrder)
  const deploymentOrder = usePlannerEditorStore((s) => s.deploymentOrder)
  const updateSinnerSkillEA = usePlannerEditorStore((s) => s.updateSinnerSkillEA)

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
      <StoreBoundDeckBuilderSummary
        onToggleDeploy={handleToggleDeploy}
        onImport={handleImport}
        onExport={handleExport}
        onResetOrder={handleResetOrder}
        onEditDeck={() => setIsDeckPaneOpen(true)}
      />

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
