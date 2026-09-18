import { DeckBuilderSummary } from '../deckBuilder/DeckBuilderSummary'
import type { EGOGiftId } from '@/shared/gameData'
import type { SinnerEquipment } from '../../types/DeckTypes'

interface DeckTrackerPanelProps {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  ownedGiftIds: ReadonlySet<EGOGiftId>
  setEquipment: React.Dispatch<React.SetStateAction<Record<string, SinnerEquipment>>>
  setDeploymentOrder: React.Dispatch<React.SetStateAction<number[]>>
  onEditDeck: () => void
  onImport: () => void
  onExport: () => void
  onResetToPreset: () => void
  onViewNotes?: (() => void) | undefined
}

export function DeckTrackerPanel({
  equipment,
  deploymentOrder,
  ownedGiftIds,
  setDeploymentOrder,
  onEditDeck,
  onImport,
  onExport,
  onResetToPreset,
  onViewNotes,
}: DeckTrackerPanelProps) {
  const handleToggleDeploy = (sinnerIndex: number) => {
    setDeploymentOrder((prev) => {
      const currentIndex = prev.indexOf(sinnerIndex)
      if (currentIndex >= 0) {
        return prev.filter((idx) => idx !== sinnerIndex)
      } else {
        return [...prev, sinnerIndex]
      }
    })
  }

  const handleClearDeployment = () => {
    setDeploymentOrder([])
  }

  return (
    <div className="space-y-4">
      <DeckBuilderSummary
        equipment={equipment}
        deploymentOrder={deploymentOrder}
        ownedGiftIds={ownedGiftIds}
        onToggleDeploy={handleToggleDeploy}
        onImport={onImport}
        onExport={onExport}
        onResetOrder={handleClearDeployment}
        onEditDeck={onEditDeck}
        trackerMode={true}
        onResetToInitial={onResetToPreset}
        onViewNotes={onViewNotes}
      />
    </div>
  )
}
