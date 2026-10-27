import { Suspense, useId, useState } from 'react'

import { showSuccess } from '@/lib/errorPresentation'

import { DEFAULT_SKILL_EA } from '@/shared/gameData'

import type { DecodedDeck } from './lib/deckCode'

import {
  PlannerEditorStoreProvider,
  usePlannerEditorStore,
  usePlannerEditorStoreApi,
} from './stores/usePlannerEditorStore'

import { useDeckClipboard } from './hooks/useDeckClipboard'
import { useIdentityMaxLevel } from './hooks/useMirrorDungeonSeason'

import { StoreBoundDeckBuilderSummary } from './components/deckBuilder/DeckBuilderSummary'
import { DeckBuilderPane } from './components/deckBuilder/DeckBuilderPane'
import { StoreBoundDeckBuilderContent } from './components/deckBuilder/DeckBuilderContent'
import { DeckImportDialog } from './components/deckBuilder/DeckImportDialog'
import { DeckBuilderPageSkeleton } from './components/plannerSkeletons'
import { PLANNER_CONFIG, SECTION_STYLES } from '@/lib/constants'

function DeckBuilderPageContent() {
  const storeApi = usePlannerEditorStoreApi()

  const setEquipment = usePlannerEditorStore((s) => s.setEquipment)
  const setDeploymentOrder = usePlannerEditorStore((s) => s.setDeploymentOrder)
  const deploymentOrder = usePlannerEditorStore((s) => s.deploymentOrder)
  const updateSinnerSkillEA = usePlannerEditorStore((s) => s.updateSinnerSkillEA)

  const [isDeckPaneOpen, setIsDeckPaneOpen] = useState(false)

  const { handleExport, openImport, importDraft, setImportText, closeImport } = useDeckClipboard({
    readDeck: () => storeApi.getState(),
    mdVersion: PLANNER_CONFIG.mdCurrentVersion,
  })

  const handleImport = () => {
    void openImport()
  }

  const handleImportApply = (deck: DecodedDeck) => {
    setEquipment(deck.equipment)
    setDeploymentOrder(deck.deploymentOrder)

    closeImport()
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

      <DeckImportDialog
        draft={importDraft}
        onTextChange={setImportText}
        onApply={handleImportApply}
        onClose={closeImport}
      />
    </div>
  )
}

function DeckBuilderPageStore() {
  const storeKey = useId()
  const identityMaxLevel = useIdentityMaxLevel(PLANNER_CONFIG.mdCurrentVersion)

  return (
    <PlannerEditorStoreProvider key={storeKey} identityMaxLevel={identityMaxLevel}>
      <DeckBuilderPageContent />
    </PlannerEditorStoreProvider>
  )
}

export default function DeckBuilderPage() {
  return (
    <Suspense fallback={<DeckBuilderPageSkeleton />}>
      <DeckBuilderPageStore />
    </Suspense>
  )
}
