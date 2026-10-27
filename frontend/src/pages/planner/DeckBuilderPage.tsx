import { Suspense, useId, useState } from 'react'

import { DEFAULT_SKILL_EA } from '@/shared/gameData'

import {
  PlannerEditorStoreProvider,
  usePlannerEditorStore,
  usePlannerEditorStoreApi,
} from './stores/usePlannerEditorStore'

import { useIdentityMaxLevel } from './hooks/useMirrorDungeonSeason'

import { StoreBoundDeckBuilderSummary } from './components/deckBuilder/DeckBuilderSummary'
import { DeckBuilderPane } from './components/deckBuilder/DeckBuilderPane'
import { StoreBoundDeckBuilderContent } from './components/deckBuilder/DeckBuilderContent'
import { DeckCodeTransfer } from './components/deckBuilder/DeckCodeTransfer'
import { DeckBuilderPageSkeleton } from './components/plannerSkeletons'
import { PLANNER_CONFIG, SECTION_STYLES } from '@/lib/constants'

function DeckBuilderPageContent() {
  const storeApi = usePlannerEditorStoreApi()

  const setEquipment = usePlannerEditorStore((s) => s.setEquipment)
  const setDeploymentOrder = usePlannerEditorStore((s) => s.setDeploymentOrder)
  const deploymentOrder = usePlannerEditorStore((s) => s.deploymentOrder)
  const updateSinnerSkillEA = usePlannerEditorStore((s) => s.updateSinnerSkillEA)

  const [isDeckPaneOpen, setIsDeckPaneOpen] = useState(false)

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
      <DeckCodeTransfer
        readDeck={() => storeApi.getState()}
        writeDeck={({ equipment, deploymentOrder }) => {
          setEquipment(equipment)
          setDeploymentOrder(deploymentOrder)
        }}
        mdVersion={PLANNER_CONFIG.mdCurrentVersion}
      >
        {({ onImport, onExport }) => (
          <>
            <StoreBoundDeckBuilderSummary
              onToggleDeploy={handleToggleDeploy}
              onImport={onImport}
              onExport={onExport}
              onResetOrder={handleResetOrder}
              onEditDeck={() => setIsDeckPaneOpen(true)}
            />

            <DeckBuilderPane open={isDeckPaneOpen} onOpenChange={setIsDeckPaneOpen}>
              <StoreBoundDeckBuilderContent
                isActive={isDeckPaneOpen}
                onImport={onImport}
                onExport={onExport}
                onResetOrder={handleResetOrder}
                onIdentityChange={(sinnerCode) => {
                  updateSinnerSkillEA(sinnerCode, { ...DEFAULT_SKILL_EA })
                }}
              />
            </DeckBuilderPane>
          </>
        )}
      </DeckCodeTransfer>
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
