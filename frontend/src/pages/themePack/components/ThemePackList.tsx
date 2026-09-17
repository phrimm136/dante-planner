import type { z } from 'zod'
import type { ThemePackList as ThemePackListType } from '../types/ThemePackTypes'
import type { ThemePackI18nSchema } from '../schemas/ThemePackSchemas'
import { THEME_PACK_GEOMETRY } from '@/shared/cardLayout'
import type { FilterStore } from '@/components/hooks/filterStore'
import { FilteredEntityGrid, useSearchTermSources } from '@/shared/filter'
import { THEME_PACK_LIST } from '../hooks/useThemePackListData'
import {
  buildThemePackSearchTerms,
  matchesThemePack,
  type ThemePackFacetState,
} from '../lib/themePackFilter'
import { toThemePackEntity } from '../lib/themePackEntity'
import { ThemePackCardLink } from './ThemePackCardLink'

const EMPTY_NAMES: z.infer<typeof ThemePackI18nSchema> = {}

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

  const sortedPacks = Object.entries(spec)
    .map(([id, entry]) => toThemePackEntity(id, entry))
    .sort((a, b) => a.id.localeCompare(b.id))

  return (
    <FilteredEntityGrid
      items={sortedPacks}
      getKey={(item) => item.id}
      store={store}
      matches={matchesThemePack}
      buildTerms={(item) => buildThemePackSearchTerms(item.id, names)}
      renderCard={(item) => <ThemePackCardLink packId={item.id} packEntry={item} />}
      emptyStateKey="themePack.emptyState"
      emptyStateFallback="No theme packs found."
      geometry={THEME_PACK_GEOMETRY}
    />
  )
}
