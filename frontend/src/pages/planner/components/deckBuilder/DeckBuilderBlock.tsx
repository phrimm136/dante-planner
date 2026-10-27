import { Suspense, startTransition, useState } from 'react'

import { DEFAULT_SKILL_EA } from '@/shared/gameData'
import {
  useDeckFilterState,
  usePlannerStore,
  useStoreDeck,
  useStoreOwnedGiftIds,
} from '../../stores/usePlannerStore'
import { DeckGridSkeleton } from '../plannerSkeletons'
import { DeckBuilderContent } from './DeckBuilderContent'
import { DeckBuilderPane } from './DeckBuilderPane'
import { DeckBuilderSummary } from './DeckBuilderSummary'
import { DeckCodeTransfer } from './DeckCodeTransfer'

export interface TrackerExtras {
  onResetToInitial: () => void
  onViewNotes?: (() => void) | undefined
}

export interface DeckBuilderBlockProps {
  tracker?: TrackerExtras | undefined
}

export function DeckBuilderBlock({ tracker }: DeckBuilderBlockProps) {
  const [paneOpen, setPaneOpen] = useState(false)
  const deck = useStoreDeck()
  const ownedGiftIds = useStoreOwnedGiftIds()
  const updateSinnerSkillEA = usePlannerStore((s) => s.updateSinnerSkillEA)
  const filterState = useDeckFilterState()
  const maxLevel = usePlannerStore((s) => s.identityMaxLevel)

  const handleToggleDeploy = (sinnerIndex: number) => {
    const currentIndex = deck.deploymentOrder.indexOf(sinnerIndex)
    if (currentIndex >= 0) {
      const newOrder = [...deck.deploymentOrder]
      newOrder.splice(currentIndex, 1)
      deck.setDeploymentOrder(newOrder)
    } else {
      deck.setDeploymentOrder([...deck.deploymentOrder, sinnerIndex])
    }
  }

  const handleResetOrder = () => {
    deck.setDeploymentOrder([])
  }

  return (
    <DeckCodeTransfer
      readDeck={() => ({ equipment: deck.equipment, deploymentOrder: deck.deploymentOrder })}
      writeDeck={({ equipment, deploymentOrder }) => {
        deck.setEquipment(() => equipment)
        deck.setDeploymentOrder(deploymentOrder)
      }}
    >
      {({ onImport, onExport }) => (
        <>
          <Suspense fallback={<DeckGridSkeleton />}>
            <DeckBuilderSummary
              equipment={deck.equipment}
              deploymentOrder={deck.deploymentOrder}
              ownedGiftIds={ownedGiftIds}
              onToggleDeploy={handleToggleDeploy}
              onImport={onImport}
              onExport={onExport}
              onResetOrder={handleResetOrder}
              onEditDeck={() => {
                startTransition(() => setPaneOpen(true))
              }}
              {...(tracker !== undefined && {
                trackerMode: true,
                onResetToInitial: tracker.onResetToInitial,
                onViewNotes: tracker.onViewNotes,
              })}
            />
          </Suspense>
          <Suspense fallback={null}>
            <DeckBuilderPane open={paneOpen} onOpenChange={setPaneOpen}>
              <DeckBuilderContent
                equipment={deck.equipment}
                setEquipment={deck.setEquipment}
                deploymentOrder={deck.deploymentOrder}
                setDeploymentOrder={deck.setDeploymentOrder}
                filterState={filterState}
                isActive={paneOpen}
                ownedGiftIds={ownedGiftIds}
                maxLevel={maxLevel}
                onImport={onImport}
                onExport={onExport}
                onResetOrder={handleResetOrder}
                onIdentityChange={(sinnerCode) => {
                  updateSinnerSkillEA(sinnerCode, { ...DEFAULT_SKILL_EA })
                }}
              />
            </DeckBuilderPane>
          </Suspense>
        </>
      )}
    </DeckCodeTransfer>
  )
}
