import { DEFAULT_DEPLOYMENT_MAX } from '@/shared/gameData'
import { SECTION_STYLES } from '@/lib/constants'

import type { EGOGiftId } from '@/shared/gameData'
import type { DeckState, EntityMode, SinnerEquipment } from '../../types/DeckTypes'
import { type SkillData } from './SinnerGrid'
import { CompactIdentityRow } from './CompactIdentityRow'
import { CompactEgoGrid } from './CompactEgoGrid'
import { StatusViewer } from './StatusViewer'
import { DeckBuilderActionBar } from './DeckBuilderActionBar'

interface DeckLoadoutSectionProps {
  entityMode: EntityMode
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  skillDataMap: Record<string, SkillData>
  egoAffinityMap: Record<string, string>
  ownedGiftIds: ReadonlySet<EGOGiftId>
  onToggleDeploy: (sinnerIndex: number) => void
  onImport: () => void
  onExport: () => void
  onResetOrder: () => void
}

export function DeckLoadoutSection({
  entityMode,
  equipment,
  deploymentOrder,
  skillDataMap,
  egoAffinityMap,
  ownedGiftIds,
  onToggleDeploy,
  onImport,
  onExport,
  onResetOrder,
}: DeckLoadoutSectionProps) {
  const deckState: DeckState = {
    equipment,
    deploymentOrder,
    deploymentConfig: {
      maxDeployed: DEFAULT_DEPLOYMENT_MAX,
    },
  }

  return (
    <div className={SECTION_STYLES.container}>
      {entityMode === 'identity' ? (
        <CompactIdentityRow
          equipment={equipment}
          deploymentOrder={deploymentOrder}
          skillDataMap={skillDataMap}
          onToggleDeploy={onToggleDeploy}
        />
      ) : (
        <CompactEgoGrid equipment={equipment} egoAffinityMap={egoAffinityMap} />
      )}
      <div className="mt-3 flex flex-col lg:flex-row lg:items-start lg:justify-between gap-3">
        <StatusViewer deckState={deckState} ownedGiftIds={ownedGiftIds} />
        <DeckBuilderActionBar onImport={onImport} onExport={onExport} onResetOrder={onResetOrder} />
      </div>
    </div>
  )
}
