import { useMemo, type Ref } from 'react'

import { CARD_GRID } from '@/lib/constants'
import { getSelectedIndicatorPath } from '@/lib/assetPaths'
import { useDeckVisibleCount } from '@/stores/usePlannerEditorStore'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
import { EGOCard } from '@/components/ego/EGOCard'
import { TierLevelSelector } from './TierLevelSelector'
import type { EGOListItem } from '@/types/EGOTypes'
import type { ThreadspinTier } from '@/types/DeckTypes'

interface EgoGridProps {
  sortedEgos: EGOListItem[]
  visibleIds: Set<string>
  equippedIds: Set<string>
  equippedThreadspinMap: Record<string, ThreadspinTier>
  onEquip: (egoId: string, data: { threadspin?: ThreadspinTier }) => void
  onUnequip: (egoId: string) => void
  scrollRef: Ref<HTMLDivElement>
  isActive: boolean
}

/**
 * EGO card grid with progressive rendering.
 *
 * Subscribes atomically to deckVisibleCount so rAF-driven progressive
 * ticks re-render only this component and its sibling IdentityGrid,
 * not the rest of the deck builder.
 */
export function EgoGrid({
  sortedEgos,
  visibleIds,
  equippedIds,
  equippedThreadspinMap,
  onEquip,
  onUnequip,
  scrollRef,
  isActive,
}: EgoGridProps) {
  const visibleCount = useDeckVisibleCount()

  const displayEgos = useMemo(
    () => sortedEgos.slice(0, visibleCount),
    [sortedEgos, visibleCount]
  )

  return (
    <div className={isActive ? '' : 'hidden'}>
      <div
        ref={scrollRef}
        className="bg-muted border border-border rounded-md p-3 lg:p-6 max-h-[600px] overflow-y-auto"
      >
        <div className="pt-4">
          <ResponsiveCardGrid
            cardWidth={CARD_GRID.WIDTH.EGO}
            cardHeight={CARD_GRID.HEIGHT.EGO}
            mobileScale={0.8}
            gap={8}
          >
            {displayEgos.map((ego) => {
              const isSelected = equippedIds.has(ego.id)
              const isVisible = visibleIds.has(ego.id)
              return (
                <div key={ego.id} className={isVisible ? '' : 'hidden'}>
                  <TierLevelSelector
                    mode="ego"
                    entityId={ego.id}
                    currentThreadspin={equippedThreadspinMap[ego.id] ?? ego.maxThreadspin}
                    maxThreadspin={ego.maxThreadspin}
                    isSelected={isSelected}
                    egoType={ego.egoType}
                    onConfirm={onEquip}
                    onUnequip={onUnequip}
                  >
                    <ScaledCardWrapper
                      mobileScale={0.8}
                      cardWidth={CARD_GRID.WIDTH.EGO}
                      cardHeight={CARD_GRID.HEIGHT.EGO}
                    >
                      <EGOCard
                        ego={ego}
                        isSelected={isSelected}
                        overlay={isSelected ? (
                          <img
                            src={getSelectedIndicatorPath()}
                            alt="Selected"
                            className="absolute inset-0 m-auto w-28 object-contain pointer-events-none"
                          />
                        ) : undefined}
                      />
                    </ScaledCardWrapper>
                  </TierLevelSelector>
                </div>
              )
            })}
          </ResponsiveCardGrid>
        </div>
      </div>
    </div>
  )
}
