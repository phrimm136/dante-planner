import { useEffect, useState, type Ref } from 'react'

import { SECTION_STYLES } from '@/lib/constants'
import { scheduleIdle } from '@/lib/scheduleIdle'

import { usePlannerEditorStoreApiSafe } from '../../stores/usePlannerEditorStore'
import type { EntityMode, ThreadspinTier, UptieTier } from '../../types/DeckTypes'
import type { EGOId, IdentityId } from '@/shared/gameData'
import type { IdentityEntity } from '@/pages/identity'
import type { EGOEntity } from '@/pages/ego'
import { DeckFilterBar } from './DeckFilterBar'
import { IdentityGrid } from './IdentityGrid'
import { EgoGrid } from './EgoGrid'

const BATCH_SIZE = 10

interface DeckCatalogSectionProps {
  isActive: boolean
  entityMode: EntityMode
  sortedIdentities: IdentityEntity[]
  visibleIdentityIds: Set<string>
  equippedIdentityIds: Set<string>
  identityScrollRef: Ref<HTMLDivElement>
  onEquipIdentity: (identityId: IdentityId, data: { uptie?: UptieTier; level?: number }) => void
  sortedEgos: EGOEntity[]
  visibleEgoIds: Set<string>
  equippedEgoIds: Set<string>
  equippedThreadspinMap: Record<string, ThreadspinTier>
  egoScrollRef: Ref<HTMLDivElement>
  onEquipEgo: (egoId: EGOId, data: { threadspin?: ThreadspinTier }) => void
  onUnequipEgo: (egoId: EGOId) => void
}

export function DeckCatalogSection({
  isActive,
  entityMode,
  sortedIdentities,
  visibleIdentityIds,
  equippedIdentityIds,
  identityScrollRef,
  onEquipIdentity,
  sortedEgos,
  visibleEgoIds,
  equippedEgoIds,
  equippedThreadspinMap,
  egoScrollRef,
  onEquipEgo,
  onUnequipEgo,
}: DeckCatalogSectionProps) {
  // Inactive grid renders nothing until after first paint; latches true until close
  const [hasWarmedInactive, setHasWarmedInactive] = useState(false)

  const storeApi = usePlannerEditorStoreApiSafe()

  // Reset progressive state on unmount so the next mount starts cold.
  // Doing this on unmount (not on mount) ensures the next mount's first
  // render already sees deckVisibleCount=BATCH_SIZE, avoiding a full-list
  // render before a post-commit reset effect can fire.
  useEffect(() => {
    if (!isActive) return
    setHasWarmedInactive(false)
    return () => {
      storeApi?.getState().setDeckVisibleCount(BATCH_SIZE)
    }
  }, [isActive, storeApi])

  useEffect(() => {
    if (!isActive || hasWarmedInactive) return
    return scheduleIdle(() => setHasWarmedInactive(true))
  }, [isActive, hasWarmedInactive])

  const totalIdentities = sortedIdentities.length
  const totalEgos = sortedEgos.length

  useEffect(() => {
    if (!isActive || !storeApi) return
    const totalCount = Math.max(totalIdentities, totalEgos)
    let raf: number | null = null

    const tick = () => {
      const current = storeApi.getState().deckVisibleCount
      if (current >= totalCount) return
      storeApi.getState().setDeckVisibleCount(Math.min(current + BATCH_SIZE, totalCount))
      raf = requestAnimationFrame(tick)
    }

    raf = requestAnimationFrame(tick)
    return () => {
      if (raf !== null) cancelAnimationFrame(raf)
    }
  }, [isActive, totalIdentities, totalEgos, storeApi])

  return (
    <div className={`${SECTION_STYLES.container} space-y-4`}>
      <DeckFilterBar />

      {(entityMode === 'identity' || hasWarmedInactive) && (
        <IdentityGrid
          sortedIdentities={sortedIdentities}
          visibleIds={visibleIdentityIds}
          equippedIds={equippedIdentityIds}
          onEquip={onEquipIdentity}
          scrollRef={identityScrollRef}
          isActive={entityMode === 'identity'}
        />
      )}

      {(entityMode === 'ego' || hasWarmedInactive) && (
        <EgoGrid
          sortedEgos={sortedEgos}
          visibleIds={visibleEgoIds}
          equippedIds={equippedEgoIds}
          equippedThreadspinMap={equippedThreadspinMap}
          onEquip={onEquipEgo}
          onUnequip={onUnequipEgo}
          scrollRef={egoScrollRef}
          isActive={entityMode === 'ego'}
        />
      )}
    </div>
  )
}
