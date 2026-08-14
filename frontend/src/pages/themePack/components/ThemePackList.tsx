import type { ThemePackList as ThemePackListType } from '../types/ThemePackTypes'
import { CARD_GRID } from '@/lib/constants'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { entriesSortedById, FilteredEntityGrid, type CardGeometry } from '@/shared/filter'
import { useThemePackI18n } from '../hooks/useThemePackListData'
import {
  buildThemePackSearchTerms,
  matchesThemePack,
  type ThemePackFacetState,
} from '../lib/themePackFilter'
import { ThemePackCardLink } from './ThemePackCardLink'

const THEME_PACK_GEOMETRY: CardGeometry = {
  cardWidth: CARD_GRID.WIDTH.THEME_PACK,
  cardHeight: CARD_GRID.HEIGHT.THEME_PACK,
  mobileScale: 0.8,
}

interface ThemePackListProps {
  spec: ThemePackListType
  store: FilterStore<ThemePackFacetState>
}

/**
 * The theme pack browser's card grid.
 *
 * Filter logic: AND between filter types, OR within each type.
 */
export function ThemePackList({ spec, store }: ThemePackListProps) {
  const themePackI18n = useThemePackI18n()

  const sortedPacks = entriesSortedById(spec)

  return (
    <FilteredEntityGrid
      items={sortedPacks}
      getKey={([packId]) => packId}
      store={store}
      matches={([, entry], state, terms) => matchesThemePack(entry, state, terms)}
      buildTerms={([packId]) => buildThemePackSearchTerms(packId, themePackI18n)}
      renderCard={([packId, entry]) => (
        <ThemePackCardLink
          packId={packId}
          packEntry={entry}
          packName={themePackI18n[packId]?.name ?? `Pack ${packId}`}
          specialName={themePackI18n[packId]?.specialName}
        />
      )}
      emptyStateKey="themePack.emptyState"
      emptyStateFallback="No theme packs found."
      geometry={THEME_PACK_GEOMETRY}
    />
  )
}
