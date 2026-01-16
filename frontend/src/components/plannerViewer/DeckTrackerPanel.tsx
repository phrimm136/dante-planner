import { DeckBuilderSummary } from '@/components/deckBuilder/DeckBuilderSummary'
import type { SinnerEquipment } from '@/types/DeckTypes'

interface DeckTrackerPanelProps {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  setEquipment: React.Dispatch<React.SetStateAction<Record<string, SinnerEquipment>>>
  setDeploymentOrder: React.Dispatch<React.SetStateAction<number[]>>
  onEditDeck: () => void
  onImport: () => void
  onExport: () => void
  onResetToPreset: () => void
  onViewNotes?: () => void
}

/**
 * Deck tracker panel for tracker mode
 *
 * Equipment display: uses DeckBuilderSummary with tracker mode
 * Deployment order: editable via toggle and arrow buttons
 * Changes are temporary (session state only)
 */
export function DeckTrackerPanel({
  equipment,
  deploymentOrder,
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
      {/* Equipment Display */}
      <DeckBuilderSummary
        equipmentOverride={equipment}
        deploymentOrderOverride={deploymentOrder}
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
