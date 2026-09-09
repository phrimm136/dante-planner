import type { z } from 'zod'
import type { ThemePackList as ThemePackListType } from '../types/ThemePackTypes'
import type { ThemePackI18nSchema } from '../schemas/ThemePackSchemas'
import { CARD_GRID } from '@/lib/constants'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import {
  entriesSortedById,
  FilteredEntityGrid,
  useSearchTermSources,
  type CardGeometry,
} from '@/shared/filter'
import { THEME_PACK_LIST } from '../hooks/useThemePackListData'
import {
  buildThemePackSearchTerms,
  matchesThemePack,
  type ThemePackFacetState,
} from '../lib/themePackFilter'
import { ThemePackCardLink } from './ThemePackCardLink'

const EMPTY_NAMES: z.infer<typeof ThemePackI18nSchema> = {}

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
  const { names } = useSearchTermSources(THEME_PACK_LIST, EMPTY_NAMES, false)

  const sortedPacks = entriesSortedById(spec)

  return (
    <FilteredEntityGrid
      items={sortedPacks}
      getKey={([packId]) => packId}
      store={store}
      matches={([, entry], state, terms) => matchesThemePack(entry, state, terms)}
      buildTerms={([packId]) => buildThemePackSearchTerms(packId, names)}
      renderCard={([packId, entry]) => <ThemePackCardLink packId={packId} packEntry={entry} />}
      emptyStateKey="themePack.emptyState"
      emptyStateFallback="No theme packs found."
      geometry={THEME_PACK_GEOMETRY}
    />
  )
}
