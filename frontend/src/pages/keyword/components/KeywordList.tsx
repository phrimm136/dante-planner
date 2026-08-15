import { memo } from 'react'
import { useTranslation } from 'react-i18next'

import { CARD_GRID, PROGRESSIVE_REVEAL, SECTION_STYLES } from '@/lib/constants'
import { KEYWORD_LIST, type BattleKeywordI18nEntry } from '@/shared/gameText'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import type { FilterStore } from '@/components/hooks/useSetFilters'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { FilteredCardSlot } from '@/shared/filter'
import { FilterEmptyState } from '@/shared/filter'
import { useSearchTermSources } from '@/shared/filter'
import {
  buildKeywordSearchTerms,
  matchesKeyword,
  type KeywordFacetItem,
  type KeywordFacetState,
} from '../lib/keywordFilter'
import { KeywordCardLink } from './KeywordCardLink'

const EMPTY_NAMES: Record<string, BattleKeywordI18nEntry> = {}

interface KeywordListItem extends KeywordFacetItem {
  id: string
  iconId: string | null
}

interface KeywordListProps {
  keywords: KeywordListItem[]
  store: FilterStore<KeywordFacetState>
}

/**
 * KeywordList - Renders every keyword card once and lets each one subscribe to its own
 * visibility, so a filter toggle re-renders only the cards that changed.
 *
 * Filter Logic:
 * - All filter types use AND between each other
 * - BuffType: OR logic (any selected buffType)
 * - Identity/EGO/EGOGift: OR logic within each, AND across entity types
 * - Search: case-insensitive substring on localized name
 */
export function KeywordList({ keywords, store }: KeywordListProps) {
  const { t } = useTranslation('database')
  const { names: keywordNames } = useSearchTermSources(KEYWORD_LIST, EMPTY_NAMES, false)

  // Progressive rendering: start with one batch, add a batch per frame
  const displayCount = useProgressiveCount({
    total: keywords.length,
    step: PROGRESSIVE_REVEAL.KEYWORD_CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.KEYWORD_CARD_BATCH,
  })

  const searchTerms = new Map(
    keywords.map((keyword) => [keyword.id, buildKeywordSearchTerms(keyword.id, keywordNames)]),
  )

  return (
    <div className={SECTION_STYLES.panel}>
      <FilterEmptyState
        store={store}
        selectEmpty={(state) =>
          !keywords.some((keyword) =>
            matchesKeyword(keyword, state, searchTerms.get(keyword.id) ?? []),
          )
        }
      >
        <div className="text-center text-muted-foreground py-8">{t('keyword.emptyState')}</div>
      </FilterEmptyState>

      <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.KEYWORD} mobileScale={0.8}>
        {keywords.slice(0, displayCount).map((keyword) => (
          <KeywordCardCell
            key={keyword.id}
            keyword={keyword}
            keywordNames={keywordNames}
            store={store}
          />
        ))}
      </ResponsiveCardGrid>
    </div>
  )
}

interface KeywordCardCellProps {
  keyword: KeywordListItem
  keywordNames: Record<string, BattleKeywordI18nEntry>
  store: FilterStore<KeywordFacetState>
}

/**
 * One keyword's slot, built inside a `map` and therefore outside the compiler's reach:
 * without `memo`, each progressive-reveal tick re-renders every card already revealed.
 *
 * The cell derives its own search terms. Taking them as a prop would defeat the
 * comparison: the list's term map is rebuilt on every render, so each array would
 * arrive with a fresh identity.
 */
const KeywordCardCell = memo(function KeywordCardCell({
  keyword,
  keywordNames,
  store,
}: KeywordCardCellProps) {
  const terms = buildKeywordSearchTerms(keyword.id, keywordNames)

  return (
    <FilteredCardSlot
      store={store}
      selectVisible={(state) => matchesKeyword(keyword, state, terms)}
      mobileScale={0.8}
      cardWidth={CARD_GRID.WIDTH.KEYWORD}
      cardHeight={CARD_GRID.HEIGHT.KEYWORD}
    >
      <KeywordCardLink id={keyword.id} iconId={keyword.iconId} buffType={keyword.buffType} />
    </FilteredCardSlot>
  )
})
