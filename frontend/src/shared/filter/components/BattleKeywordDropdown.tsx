import { useTranslation } from 'react-i18next'

import { useKeywordListSpec, useKeywordListI18n } from '@/shared/gameText'
import { SearchableMultiSelect } from './SearchableMultiSelect'

type EntityType = 'identity' | 'ego' | 'egoGift'

interface BattleKeywordDropdownProps {
  entityType: EntityType
  selected: Set<string>
  onSelectionChange: (keywords: Set<string>) => void
  className?: string
}

/** Map entity type to the backlink field in BattleKeywordSpecEntry */
const BACKLINK_FIELD: Record<EntityType, 'identities' | 'egos' | 'egoGifts'> = {
  identity: 'identities',
  ego: 'egos',
  egoGift: 'egoGifts',
}

/**
 * Searchable multi-select dropdown for battle keyword filtering.
 * Scopes options to keywords that have at least one entity of the given type.
 *
 * Suspends while loading - wrap in Suspense boundary.
 */
export function BattleKeywordDropdown({
  entityType,
  selected,
  onSelectionChange,
  className,
}: BattleKeywordDropdownProps) {
  const { t } = useTranslation(['database', 'common'])
  const spec = useKeywordListSpec()
  const i18n = useKeywordListI18n()

  const backlinkField = BACKLINK_FIELD[entityType]

  const options = Object.entries(spec)
    .filter(([, entry]) => entry[backlinkField].length > 0)
    .map(([keywordId, entry]) => ({
      value: keywordId,
      label: i18n[keywordId]?.name ?? keywordId,
      count: entry[backlinkField].length,
    }))

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selected}
      onSelectionChange={onSelectionChange}
      placeholder={t('filters.additionalKeyword', 'Additional Keywords')}
      searchPlaceholder={t('filters.searchAdditionalKeyword', 'Search keywords...')}
      className={className}
    />
  )
}
