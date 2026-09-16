import { useStore } from 'zustand'

import { CardSlot, type CardSizePx } from '@/shared/cardLayout'
import type { FilterState, FilterStore } from '@/components/hooks/filterStore'

interface FilteredCardSlotProps<T> {
  store: FilterStore<T>
  /** Whether this one card survives the current filters */
  selectVisible: (state: FilterState<T>) => boolean
  /** The card's box */
  size: CardSizePx
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
 *   size={IDENTITY_GEOMETRY.size}
 *   mobileScale={0.8}
 * >
 *   <IdentityCardLink identity={identity} />
 * </FilteredCardSlot>
 */
export function FilteredCardSlot<T>({
  store,
  selectVisible,
  size,
  mobileScale,
  children,
}: FilteredCardSlotProps<T>) {
  const visible = useStore(store, selectVisible)

  return (
    <CardSlot size={size} mobileScale={mobileScale} className={visible ? '' : 'hidden'}>
      {children}
    </CardSlot>
  )
}
