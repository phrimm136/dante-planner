import { useTranslation } from 'react-i18next'
import { ArrowUp, ArrowDown } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { DeckBuilderSummary } from '@/components/deckBuilder/DeckBuilderSummary'
import { SINNERS } from '@/lib/constants'
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
  setEquipment,
  setDeploymentOrder,
  onEditDeck,
  onImport,
  onExport,
  onResetToPreset,
  onViewNotes,
}: DeckTrackerPanelProps) {
  const { t } = useTranslation(['planner', 'common'])

  const handleToggleDeploy = (sinnerIndex: number) => {
    setDeploymentOrder((prev) => {
      const currentIndex = prev.indexOf(sinnerIndex)
      if (currentIndex >= 0) {
        // Remove from deployment
        return prev.filter((idx) => idx !== sinnerIndex)
      } else {
        // Add to deployment
        return [...prev, sinnerIndex]
      }
    })
  }

  const handleClearDeployment = () => {
    // Clear all deployment (set to empty array)
    setDeploymentOrder([])
  }

  const handleResetToPreset = () => {
    // Reset to planner's preset deployment
    onResetToPreset()
  }

  const handleMoveUp = (index: number) => {
    if (index === 0) return
    setDeploymentOrder((prev) => {
      const newOrder = [...prev]
      ;[newOrder[index - 1], newOrder[index]] = [newOrder[index], newOrder[index - 1]]
      return newOrder
    })
  }

  const handleMoveDown = (index: number) => {
    if (index === deploymentOrder.length - 1) return
    setDeploymentOrder((prev) => {
      const newOrder = [...prev]
      ;[newOrder[index], newOrder[index + 1]] = [newOrder[index + 1], newOrder[index]]
      return newOrder
    })
  }

  return (
    <div className="space-y-4">
      {/* Equipment Display */}
      <DeckBuilderSummary
        equipment={equipment}
        deploymentOrder={deploymentOrder}
        onToggleDeploy={handleToggleDeploy}
        onImport={onImport}
        onExport={onExport}
        onResetOrder={handleClearDeployment}
        onEditDeck={onEditDeck}
        trackerMode={true}
        onResetToInitial={handleResetToPreset}
        onViewNotes={onViewNotes}
      />
    </div>
  )
}
