import { useStore } from 'zustand'
import { ScaledCardWrapper } from '@/components/layout/ScaledCardWrapper'
import type { FilterState, FilterStore } from '@/components/hooks/useSetFilters'

interface FilteredCardSlotProps<T> {
  store: FilterStore<T>
  /** Whether this one card survives the current filters */
  selectVisible: (state: FilterState<T>) => boolean
  cardWidth: number
  cardHeight: number
  mobileScale: number
  children: React.ReactNode
}

/**
 * One card's seat in a filtered grid, subscribed to its own visibility.
 *
 * The grid hands every slot the same store and an item-specific predicate, so a filter
 * change re-renders only the cards whose visibility actually flipped — the grid itself
 * renders once, at mount.
 *
 * @example
 * <FilteredCardSlot
 *   key={identity.id}
 *   store={store}
 *   selectVisible={(state) => matchesIdentity(identity, state, terms)}
 *   cardWidth={CARD_GRID.WIDTH.IDENTITY}
 *   cardHeight={CARD_GRID.HEIGHT.IDENTITY}
 *   mobileScale={0.8}
 * >
 *   <IdentityCardLink identity={identity} />
 * </FilteredCardSlot>
 */
export function FilteredCardSlot<T>({
  store,
  selectVisible,
  cardWidth,
  cardHeight,
  mobileScale,
  children,
}: FilteredCardSlotProps<T>) {
  const visible = useStore(store, selectVisible)

  return (
    <ScaledCardWrapper
      mobileScale={mobileScale}
      cardWidth={cardWidth}
      cardHeight={cardHeight}
      className={visible ? '' : 'hidden'}
    >
      {children}
    </ScaledCardWrapper>
  )
}
