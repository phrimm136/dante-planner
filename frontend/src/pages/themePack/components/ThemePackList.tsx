import { memo } from 'react'
import { useTranslation } from 'react-i18next'

import type { ThemePackList as ThemePackListType } from '../types/ThemePackTypes'
import { CARD_GRID, PROGRESSIVE_REVEAL, SECTION_STYLES } from '@/lib/constants'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { useThemePackI18n } from '../hooks/useThemePackListData'
import {
  buildThemePackSearchTerms,
  matchesThemePack,
  type ThemePackFacetState,
} from '../lib/themePackFilter'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { FilteredCardSlot } from '@/shared/filter'
import { FilterEmptyState } from '@/shared/filter'
import { ThemePackCardLink } from './ThemePackCardLink'

interface ThemePackListProps {
  spec: ThemePackListType
  store: FilterStore<ThemePackFacetState>
}

/**
 * ThemePackList - Renders every theme pack card once and lets each one subscribe to its
 * own visibility, so a filter toggle re-renders only the cards that changed.
 *
 * Filter logic: AND between filter types, OR within each type.
 */
export function ThemePackList({ spec, store }: ThemePackListProps) {
  const { t } = useTranslation('database')
  const themePackI18n = useThemePackI18n()

  // Build sorted pack list
  const sortedPacks = Object.entries(spec).sort(([a], [b]) => a.localeCompare(b))

  // Progressive rendering
  const displayCount = useProgressiveCount({
    total: sortedPacks.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
  })

  const searchTerms = new Map(
    sortedPacks.map(([packId]) => [packId, buildThemePackSearchTerms(packId, themePackI18n)]),
  )

  return (
    <div className={SECTION_STYLES.panel}>
      <FilterEmptyState
        store={store}
        selectEmpty={(state) =>
          !sortedPacks.some(([packId, entry]) =>
            matchesThemePack(entry, state, searchTerms.get(packId) ?? []),
          )
        }
      >
        <div className="text-center text-muted-foreground py-8">
          {t('themePack.emptyState', 'No theme packs found.')}
        </div>
      </FilterEmptyState>

      <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.THEME_PACK} mobileScale={0.8}>
        {sortedPacks.slice(0, displayCount).map(([packId, entry]) => (
          <ThemePackCardCell
            key={packId}
            packId={packId}
            entry={entry}
            themePackI18n={themePackI18n}
            store={store}
          />
        ))}
      </ResponsiveCardGrid>
    </div>
  )
}

interface ThemePackCardCellProps {
  packId: string
  entry: ThemePackListType[string]
  themePackI18n: ReturnType<typeof useThemePackI18n>
  store: FilterStore<ThemePackFacetState>
}

/**
 * One pack's slot, built inside a `map` and therefore outside the compiler's reach:
 * without `memo`, each progressive-reveal tick re-renders every card already revealed.
 *
 * The cell derives its own search terms. Taking them as a prop would defeat the
 * comparison: the list's term map is rebuilt on every render, so each array would
 * arrive with a fresh identity.
 */
const ThemePackCardCell = memo(function ThemePackCardCell({
  packId,
  entry,
  themePackI18n,
  store,
}: ThemePackCardCellProps) {
  const terms = buildThemePackSearchTerms(packId, themePackI18n)
  const i18nEntry = themePackI18n[packId]

  return (
    <FilteredCardSlot
      store={store}
      selectVisible={(state) => matchesThemePack(entry, state, terms)}
      mobileScale={0.8}
      cardWidth={CARD_GRID.WIDTH.THEME_PACK}
      cardHeight={CARD_GRID.HEIGHT.THEME_PACK}
    >
      <ThemePackCardLink
        packId={packId}
        packEntry={entry}
        packName={i18nEntry?.name ?? `Pack ${packId}`}
        specialName={i18nEntry?.specialName}
      />
    </FilteredCardSlot>
  )
})
