import { useMemo, type Ref } from 'react'

import { CARD_GRID, MAX_LEVEL } from '@/lib/constants'
import { getSelectedIndicatorPath } from '@/lib/assetPaths'
import { useDeckVisibleCount } from '@/stores/usePlannerEditorStore'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
import { IdentityCard } from '@/pages/identity'
import { TierLevelSelector } from './TierLevelSelector'
import type { IdentityListItem } from '@/pages/identity'
import type { UptieTier } from '@/types/DeckTypes'

interface IdentityGridProps {
  sortedIdentities: IdentityListItem[]
  visibleIds: Set<string>
  equippedIds: Set<string>
  onEquip: (identityId: string, data: { uptie?: UptieTier; level?: number }) => void
  scrollRef: Ref<HTMLDivElement>
  isActive: boolean
}

/**
 * Identity card grid with progressive rendering.
 *
 * Subscribes atomically to deckVisibleCount so rAF-driven progressive
 * ticks re-render only this component and its sibling EgoGrid,
 * not the rest of the deck builder.
 */
export function IdentityGrid({
  sortedIdentities,
  visibleIds,
  equippedIds,
  onEquip,
  scrollRef,
  isActive,
}: IdentityGridProps) {
  const visibleCount = useDeckVisibleCount()

  const displayIdentities = useMemo(
    () => sortedIdentities.slice(0, visibleCount),
    [sortedIdentities, visibleCount]
  )

  return (
    <div className={isActive ? '' : 'hidden'}>
      <div
        ref={scrollRef}
        className="bg-muted border border-border rounded-md p-3 lg:p-6 max-h-[600px] overflow-y-auto"
      >
        <div className="pt-4">
          <ResponsiveCardGrid
            cardWidth={CARD_GRID.WIDTH.IDENTITY}
            cardHeight={CARD_GRID.HEIGHT.IDENTITY}
            mobileScale={0.8}
            gap={8}
          >
            {displayIdentities.map((identity) => {
              const isSelected = equippedIds.has(identity.id)
              const isVisible = visibleIds.has(identity.id)
              return (
                <div key={identity.id} className={isVisible ? '' : 'hidden'}>
                  <TierLevelSelector
                    mode="identity"
                    entityId={identity.id}
                    currentUptie={4}
                    currentLevel={MAX_LEVEL}
                    isSelected={isSelected}
                    onConfirm={onEquip}
                  >
                    <ScaledCardWrapper
                      mobileScale={0.8}
                      cardWidth={CARD_GRID.WIDTH.IDENTITY}
                      cardHeight={CARD_GRID.HEIGHT.IDENTITY}
                    >
                      <IdentityCard
                        identity={identity}
                        isSelected={isSelected}
                        overlay={isSelected ? (
                          <img
                            src={getSelectedIndicatorPath()}
                            alt="Selected"
                            className="absolute inset-0 m-auto w-38 object-contain pointer-events-none"
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
