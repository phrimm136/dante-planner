import { useTranslation } from 'react-i18next'

import { useThemePackListI18n } from '@/shared/themePack'
import { SearchableMultiSelect, ThemePackDropdown } from '@/shared/filter'
import { useBattleFacetNames } from '../hooks/useBattleFacetNames'
import {
  BATTLE_FAMILIES,
  HIDDEN_BATTLE,
  type BattleFamily,
  type HiddenBattle,
} from '../lib/battleVocabulary'
import {
  chapterLabel,
  luxcavationLabel,
  railwayLineLabel,
  type Translate,
} from '../lib/enemy/enemyFacetOptions'
import { OptionToggleGroup, type ToggleOption } from './OptionToggleGroup'

interface ControlProps<T> {
  selected: Set<T>
  onSelectionChange: (next: Set<T>) => void
}

interface KeyedControlProps<T> extends ControlProps<T> {
  keys: readonly T[]
}

export function TypeFilter({ selected, onSelectionChange }: ControlProps<BattleFamily>) {
  const { t } = useTranslation('database')
  const options = BATTLE_FAMILIES.map((family) => ({
    value: family,
    label: t(`battle.type.${family}`),
  }))

  return (
    <OptionToggleGroup
      options={options}
      selected={selected}
      onSelectionChange={onSelectionChange}
    />
  )
}

export function HiddenBattleFilter({ selected, onSelectionChange }: ControlProps<HiddenBattle>) {
  const { t } = useTranslation('database')
  const options: ToggleOption<HiddenBattle>[] = [
    { value: HIDDEN_BATTLE, label: t('battle.facet.hidden') },
  ]

  return (
    <OptionToggleGroup
      options={options}
      selected={selected}
      onSelectionChange={onSelectionChange}
    />
  )
}

export function ChapterFilter({ keys, selected, onSelectionChange }: KeyedControlProps<string>) {
  const { t } = useTranslation('database')
  const translate: Translate = (key, options) => t(key, options)
  const names = useBattleFacetNames()
  const options = keys.map((key) => ({
    value: key,
    label: chapterLabel(key, names.chapters, translate),
  }))

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selected}
      onSelectionChange={onSelectionChange}
      placeholder={t('battle.facet.chapter')}
      searchPlaceholder={t('pages.battle.searchBar')}
      sortByLabel={false}
    />
  )
}

export function EnemyThemePackFilter({
  keys,
  selected,
  onSelectionChange,
}: KeyedControlProps<string>) {
  const names = useThemePackListI18n()
  const packs = Object.fromEntries(keys.map((key) => [key, {}]))

  return (
    <ThemePackDropdown
      selected={selected}
      onSelectionChange={onSelectionChange}
      packs={packs}
      names={names}
    />
  )
}

export function EventFilter({ keys, selected, onSelectionChange }: KeyedControlProps<string>) {
  const { t } = useTranslation('database')
  const names = useBattleFacetNames()
  const options = keys.map((key) => {
    const label = names.events[key] ?? key
    return { value: key, label, renderLabel: <span className="line-clamp-1">{label}</span> }
  })

  return (
    <SearchableMultiSelect
      options={options}
      selectedValues={selected}
      onSelectionChange={onSelectionChange}
      placeholder={t('battle.facet.event')}
      searchPlaceholder={t('pages.battle.searchBar')}
      sortByLabel={false}
    />
  )
}

export function RailwayLineFilter({
  keys,
  selected,
  onSelectionChange,
}: KeyedControlProps<number>) {
  const { t } = useTranslation('database')
  const translate: Translate = (key, options) => t(key, options)
  const names = useBattleFacetNames()
  const options = keys.map((line) => ({
    value: line,
    label: railwayLineLabel(line, names.railwayLines, translate),
  }))

  return (
    <OptionToggleGroup
      options={options}
      selected={selected}
      onSelectionChange={onSelectionChange}
    />
  )
}

export function LuxcavationFilter({
  keys,
  selected,
  onSelectionChange,
}: KeyedControlProps<string>) {
  const { t } = useTranslation('database')
  const translate: Translate = (key, options) => t(key, options)
  const names = useBattleFacetNames()
  const options = keys.map((key) => ({
    value: key,
    label: luxcavationLabel(key, names.luxcavations, translate),
  }))

  return (
    <OptionToggleGroup
      options={options}
      selected={selected}
      onSelectionChange={onSelectionChange}
    />
  )
}
