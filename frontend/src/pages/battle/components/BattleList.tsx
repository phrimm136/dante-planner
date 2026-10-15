import type { FilterStore } from '@/components/hooks/filterStore'
import { useTranslation } from 'react-i18next'
import { IDENTITY_LIST } from '@/pages/identity'
import { FilteredEntityGrid, useSearchTermSources } from '@/shared/filter'
import { ASSISTANT_LIST } from '../hooks/useAssistantListData'
import { BATTLE_FACET_NAMES } from '../hooks/useBattleFacetNames'
import { ENEMY_LIST } from '../hooks/useEnemyListData'
import { SINNER_EVENT_LIST } from '../hooks/useSinnerEventListData'
import { matchesBattle, type BattleFacetState } from '../lib/battleFilter'
import {
  battleEventTitle,
  buildBattleSearchTerms,
  cardPersonalityId,
  envyIdentityTitle,
  type BattleCardItem,
} from '../lib/battleListing'
import type { BattleCase, BattleFamily } from '../lib/battleVocabulary'
import { BATTLE_GEOMETRY } from '../lib/cardLayout'
import type { BattleFacetNames, BattleNameList } from '../schemas/BattleSpecSchemas'
import { BattleCard } from './BattleCard'

const EMPTY_NAMES: BattleNameList = {}
const EMPTY_IDENTITY_TITLES: Record<string, string> = {}
const EMPTY_FACET_NAMES: BattleFacetNames = {
  chapters: {},
  railwayLines: {},
  luxcavations: {},
  events: {},
}

interface BattleListProps {
  items: readonly BattleCardItem[]
  mode: BattleCase
  store: FilterStore<BattleFacetState>
}

export function BattleList({ items, mode, store }: BattleListProps) {
  const names: Record<BattleFamily, BattleNameList> = {
    enemy: useSearchTermSources(ENEMY_LIST, EMPTY_NAMES, false).names,
    assistant: useSearchTermSources(ASSISTANT_LIST, EMPTY_NAMES, false).names,
    sinnerEvent: useSearchTermSources(SINNER_EVENT_LIST, EMPTY_NAMES, false).names,
  }
  const { t } = useTranslation('database')
  const identityTitles = useSearchTermSources(IDENTITY_LIST, EMPTY_IDENTITY_TITLES, false).names
  const chapterNames = useSearchTermSources(BATTLE_FACET_NAMES, EMPTY_FACET_NAMES, false).names
    .chapters
  const identityTitle = (item: BattleCardItem) =>
    envyIdentityTitle(cardPersonalityId(item), identityTitles)
  const eventTitle = (item: BattleCardItem) =>
    battleEventTitle(item.family, item.entity.chapters, chapterNames, (key, options) =>
      t(key, options),
    )

  return (
    <FilteredEntityGrid
      items={items}
      getKey={(item) => `${item.family}-${item.entity.id}`}
      store={store}
      matches={matchesBattle}
      buildTerms={(item) =>
        buildBattleSearchTerms(
          item.entity.id,
          names[item.family],
          mode,
          identityTitle(item),
          eventTitle(item),
        )
      }
      renderCard={(item) => (
        <BattleCard
          family={item.family}
          id={item.entity.id}
          mode={mode}
          identityTitle={identityTitle(item)}
          enableHoverHighlight
          enableDetailLink
        />
      )}
      emptyStateKey="battle.emptyState"
      emptyStateFallback="No battle entities found."
      geometry={BATTLE_GEOMETRY}
    />
  )
}
