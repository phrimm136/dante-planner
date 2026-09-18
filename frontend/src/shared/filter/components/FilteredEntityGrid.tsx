import { memo, useRef, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { PROGRESSIVE_REVEAL, SECTION_STYLES } from '@/lib/constants'
import { useRevealWindow } from '@/components/hooks/useRevealWindow'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import type { CardGeometry } from '@/shared/cardLayout'
import type { FilterState, FilterStore } from '@/components/hooks/filterStore'
import { FilterEmptyState } from './FilterEmptyState'
import { FilteredCardSlot } from './FilteredCardSlot'

const NO_TERMS: readonly string[] = []

interface FilteredEntityGridProps<TItem, TState> {
  items: readonly TItem[]
  getKey: (item: TItem) => string
  store: FilterStore<TState>
  matches: (item: TItem, state: FilterState<TState>, terms: readonly string[]) => boolean
  buildTerms?: (item: TItem) => string[]
  renderCard: (item: TItem) => ReactNode
  emptyStateKey: string
  emptyStateFallback?: string
  geometry: CardGeometry
  gridWrapperClassName?: string
}

export function FilteredEntityGrid<TItem, TState>({
  items,
  getKey,
  store,
  matches,
  buildTerms,
  renderCard,
  emptyStateKey,
  emptyStateFallback,
  geometry,
  gridWrapperClassName,
}: FilteredEntityGridProps<TItem, TState>) {
  const { t } = useTranslation('database')
  const gridRef = useRef<HTMLDivElement>(null)

  const isRevealed = useRevealWindow({
    total: items.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    gridRef,
  })

  const termsByKey = new Map(items.map((item) => [getKey(item), buildTerms?.(item) ?? NO_TERMS]))

  const grid = (
    <ResponsiveCardGrid
      ref={gridRef}
      size={geometry.size}
      rows={geometry.rows}
      mobileScale={geometry.mobileScale}
    >
      {items.map((item, index) => (
        <FilteredEntityCell
          key={getKey(item)}
          item={item}
          store={store}
          matches={matches}
          buildTerms={buildTerms}
          renderCard={renderCard}
          geometry={geometry}
          revealed={isRevealed(index)}
        />
      ))}
    </ResponsiveCardGrid>
  )

  return (
    <div className={SECTION_STYLES.panel}>
      <FilterEmptyState
        store={store}
        selectEmpty={(state) =>
          !items.some((item) => matches(item, state, termsByKey.get(getKey(item)) ?? NO_TERMS))
        }
      >
        <div className="text-center text-muted-foreground py-8">
          {t(emptyStateKey, { defaultValue: emptyStateFallback })}
        </div>
      </FilterEmptyState>

      {gridWrapperClassName === undefined ? (
        grid
      ) : (
        <div className={gridWrapperClassName}>{grid}</div>
      )}
    </div>
  )
}

interface FilteredEntityCellProps<TItem, TState> {
  item: TItem
  store: FilterStore<TState>
  matches: (item: TItem, state: FilterState<TState>, terms: readonly string[]) => boolean
  buildTerms?: ((item: TItem) => string[]) | undefined
  renderCard: (item: TItem) => ReactNode
  geometry: CardGeometry
  revealed: boolean
}

/**
 * One item's slot, built inside a `map` and therefore outside the compiler's reach:
 * without `memo`, each reveal tick re-renders every card already revealed.
 *
 * The cell derives its own search terms. Taking them as a prop would defeat the
 * comparison: the grid's term map is rebuilt on every render, so each array would
 * arrive with a fresh identity.
 */
function FilteredEntityCellInner<TItem, TState>({
  item,
  store,
  matches,
  buildTerms,
  renderCard,
  geometry,
  revealed,
}: FilteredEntityCellProps<TItem, TState>) {
  const terms = buildTerms?.(item) ?? NO_TERMS

  return (
    <FilteredCardSlot
      store={store}
      selectVisible={(state) => matches(item, state, terms)}
      mobileScale={geometry.mobileScale}
      size={geometry.size}
    >
      {revealed ? renderCard(item) : null}
    </FilteredCardSlot>
  )
}

const FilteredEntityCell = memo(FilteredEntityCellInner) as typeof FilteredEntityCellInner
