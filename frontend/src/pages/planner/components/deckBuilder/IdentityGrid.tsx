import { memo, type Ref } from 'react'

import { MAX_LEVEL } from '@/shared/gameData'
import { CARD_GRID } from '@/lib/constants'
import { getSelectedIndicatorPath } from '@/shared/assets'
import { useDeckVisibleCount } from '../../stores/usePlannerEditorStore'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { ScaledCardWrapper } from '@/components/layout/ScaledCardWrapper'
import { IdentityCard } from '@/pages/identity'
import { IdentityTierSelector } from './EntityTierSelectors'
import type { IdentityListItem } from '@/pages/identity'
import type { UptieTier } from '../../types/DeckTypes'

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
 *
 * The rows take the filter and equip sets whole and read their own entry, so a
 * progressive tick — which changes neither — leaves every already-revealed card
 * at zero.
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

  const displayIdentities = sortedIdentities.slice(0, visibleCount)

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
            {displayIdentities.map((identity) => (
              <IdentityGridCard
                key={identity.id}
                identity={identity}
                visibleIds={visibleIds}
                equippedIds={equippedIds}
                onEquip={onEquip}
              />
            ))}
          </ResponsiveCardGrid>
        </div>
      </div>
    </div>
  )
}

interface IdentityGridCardProps {
  identity: IdentityListItem
  visibleIds: Set<string>
  equippedIds: Set<string>
  onEquip: (identityId: string, data: { uptie?: UptieTier; level?: number }) => void
}

/**
 * The compiler cannot cache an element built inside a `map`, so without `memo`
 * every revealed card re-renders on every progressive tick.
 */
const IdentityGridCard = memo(function IdentityGridCard({
  identity,
  visibleIds,
  equippedIds,
  onEquip,
}: IdentityGridCardProps) {
  const isSelected = equippedIds.has(identity.id)
  const isVisible = visibleIds.has(identity.id)

  return (
    <div className={isVisible ? '' : 'hidden'}>
      <IdentityTierSelector
        entityId={identity.id}
        currentUptie={4}
        currentLevel={MAX_LEVEL}
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
            overlay={
              isSelected ? (
                <img
                  src={getSelectedIndicatorPath()}
                  alt="Selected"
                  className="absolute inset-0 m-auto w-38 object-contain pointer-events-none"
                />
              ) : undefined
            }
          />
        </ScaledCardWrapper>
      </IdentityTierSelector>
    </div>
  )
})
