import { memo, type Ref } from 'react'
import type { EGOId } from '@/shared/gameData'

import { CARD_MOBILE_SCALE } from '@/lib/constants'
import { useDeckVisibleCount } from '../../stores/usePlannerEditorStore'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { CardSlot, EGO_GEOMETRY } from '@/shared/cardLayout'
import { EGOCard, EGOSelectedTag } from '@/pages/ego'
import { EgoThreadspinSelector } from './EntityTierSelectors'
import type { EGOListItem } from '@/pages/ego'
import type { ThreadspinTier } from '../../types/DeckTypes'

/** The ego box with its height left to the card. */
interface EgoGridProps {
  sortedEgos: EGOListItem[]
  visibleIds: Set<string>
  equippedIds: Set<string>
  equippedThreadspinMap: Record<string, ThreadspinTier>
  onEquip: (egoId: EGOId, data: { threadspin?: ThreadspinTier }) => void
  onUnequip: (egoId: EGOId) => void
  scrollRef: Ref<HTMLDivElement>
  isActive: boolean
}

/**
 * EGO card grid with progressive rendering.
 *
 * Subscribes atomically to deckVisibleCount so rAF-driven progressive
 * ticks re-render only this component and its sibling IdentityGrid,
 * not the rest of the deck builder.
 *
 * The rows take the filter and equip sets whole and read their own entry, so a
 * progressive tick — which changes neither — leaves every already-revealed card
 * at zero.
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

  const displayEgos = sortedEgos.slice(0, visibleCount)

  return (
    <div className={isActive ? '' : 'hidden'}>
      <div
        ref={scrollRef}
        className="bg-muted border border-border rounded-md p-3 lg:p-6 max-h-[600px] overflow-y-auto"
      >
        <div className="pt-4">
          <ResponsiveCardGrid size={EGO_GEOMETRY.size} mobileScale={CARD_MOBILE_SCALE} gap={8}>
            {displayEgos.map((ego) => (
              <EgoGridCard
                key={ego.id}
                ego={ego}
                visibleIds={visibleIds}
                equippedIds={equippedIds}
                equippedThreadspinMap={equippedThreadspinMap}
                onEquip={onEquip}
                onUnequip={onUnequip}
              />
            ))}
          </ResponsiveCardGrid>
        </div>
      </div>
    </div>
  )
}

interface EgoGridCardProps {
  ego: EGOListItem
  visibleIds: Set<string>
  equippedIds: Set<string>
  equippedThreadspinMap: Record<string, ThreadspinTier>
  onEquip: (egoId: EGOId, data: { threadspin?: ThreadspinTier }) => void
  onUnequip: (egoId: EGOId) => void
}

/**
 * The compiler cannot cache an element built inside a `map`, so without `memo`
 * every revealed card re-renders on every progressive tick.
 */
const EgoGridCard = memo(function EgoGridCard({
  ego,
  visibleIds,
  equippedIds,
  equippedThreadspinMap,
  onEquip,
  onUnequip,
}: EgoGridCardProps) {
  const isSelected = equippedIds.has(ego.id)
  const isVisible = visibleIds.has(ego.id)

  return (
    <div className={isVisible ? '' : 'hidden'}>
      <EgoThreadspinSelector
        entityId={ego.id}
        currentThreadspin={equippedThreadspinMap[ego.id] ?? ego.maxThreadspin}
        maxThreadspin={ego.maxThreadspin}
        isSelected={isSelected}
        onConfirm={onEquip}
        onUnequip={onUnequip}
      >
        <CardSlot size={EGO_GEOMETRY.size}>
          <EGOCard
            ego={ego}
            isSelected={isSelected}
            overlay={isSelected ? <EGOSelectedTag /> : undefined}
          />
        </CardSlot>
      </EgoThreadspinSelector>
    </div>
  )
})
