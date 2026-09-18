import { useStore } from 'zustand'

import { CardSlot, type CardSizePx } from '@/shared/cardLayout'
import type { FilterState, FilterStore } from '@/components/hooks/filterStore'

interface FilteredCardSlotProps<T> {
  store: FilterStore<T>
  selectVisible: (state: FilterState<T>) => boolean
  size: CardSizePx
  mobileScale: number
  children: React.ReactNode
}

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
