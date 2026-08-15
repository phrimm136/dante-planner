import { memo, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { PROGRESSIVE_REVEAL, SECTION_STYLES } from '@/lib/constants'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import type { FilterState, FilterStore } from '@/components/hooks/useSetFilters'
import { FilterEmptyState } from './FilterEmptyState'
import { FilteredCardSlot } from './FilteredCardSlot'

const NO_TERMS: readonly string[] = []

/**
 * A list's card sizing, read by both the grid and every cell in it.
 *
 * Cells are memoized on it, so it must keep a stable identity across renders —
 * declare it once per list rather than inline at the call site.
 */
export interface CardGeometry {
  cardWidth: number
  cardHeight: number
  mobileScale: number
  /** Pins grid rows to the card height. Omitted for variable-height cards. */
  fixedRowHeight?: boolean
}

interface FilteredEntityGridProps<TItem, TState> {
  /** Items in final render order — the grid sorts nothing. */
  items: readonly TItem[]
  /** One item's stable identity: its React key and its search-term cache key. */
  getKey: (item: TItem) => string
  store: FilterStore<TState>
  /** Whether one item survives the current filter state. */
  matches: (item: TItem, state: FilterState<TState>, terms: readonly string[]) => boolean
  /** One item's lowercased search terms. Omitted by lists with no search box. */
  buildTerms?: (item: TItem) => string[]
  /** The card that fills one item's slot. */
  renderCard: (item: TItem) => ReactNode
  /** Message shown while nothing matches, keyed in the `database` namespace. */
  emptyStateKey: string
  emptyStateFallback?: string
  geometry: CardGeometry
  /** Class of the element wrapping the grid. Omitted leaves the grid unwrapped. */
  gridWrapperClassName?: string
}

/**
 * A filtered card grid: renders every item once and lets each card subscribe to its own
 * visibility, so a filter toggle re-renders only the cards that changed.
 *
 * @example
 * <FilteredEntityGrid
 *   items={sortedEGOs}
 *   getKey={(ego) => ego.id}
 *   store={store}
 *   matches={matchesEGO}
 *   buildTerms={(ego) => buildEGOSearchTerms(ego, egoNames, mappings)}
 *   renderCard={(ego) => <EGOCardLink ego={ego} />}
 *   emptyStateKey="ego.emptyState"
 *   geometry={EGO_GEOMETRY}
 *   gridWrapperClassName="pt-4"
 * />
 */
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

  // Progressive rendering: start with one batch, add a batch per frame
  const displayCount = useProgressiveCount({
    total: items.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
  })

  const termsByKey = new Map(items.map((item) => [getKey(item), buildTerms?.(item) ?? NO_TERMS]))

  const grid = (
    <ResponsiveCardGrid
      cardWidth={geometry.cardWidth}
      {...(geometry.fixedRowHeight === true && { cardHeight: geometry.cardHeight })}
      mobileScale={geometry.mobileScale}
    >
      {items.slice(0, displayCount).map((item) => (
        <FilteredEntityCell
          key={getKey(item)}
          item={item}
          store={store}
          matches={matches}
          buildTerms={buildTerms}
          renderCard={renderCard}
          geometry={geometry}
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
}

/**
 * One item's slot, built inside a `map` and therefore outside the compiler's reach:
 * without `memo`, each progressive-reveal tick re-renders every card already revealed.
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
}: FilteredEntityCellProps<TItem, TState>) {
  const terms = buildTerms?.(item) ?? NO_TERMS

  return (
    <FilteredCardSlot
      store={store}
      selectVisible={(state) => matches(item, state, terms)}
      mobileScale={geometry.mobileScale}
      cardWidth={geometry.cardWidth}
      cardHeight={geometry.cardHeight}
    >
      {renderCard(item)}
    </FilteredCardSlot>
  )
}

const FilteredEntityCell = memo(FilteredEntityCellInner) as typeof FilteredEntityCellInner
