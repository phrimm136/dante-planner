import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { Skeleton } from '@/components/ui/skeleton'
import { useThemePackListI18n } from '@/shared/themePack'
import { useBattleFacetNames } from '../../hooks/useBattleFacetNames'
import { useBattleStageListI18n, useBattleStageListSpec } from '../../hooks/useBattleStageData'
import { rowContentKey } from '../../lib/battleDetail'
import type { Translate } from '../../lib/enemy/enemyFacetOptions'
import { engagementLabel, suffixedLabel } from '../../lib/engagementLabels'
import { selectorEntries, type SelectorRowGroup } from '../../lib/engagementRows'
import { SelectorGroup } from './BattleSelectorRow'

interface BattleEngagementRowsProps {
  groups: readonly SelectorRowGroup[]
  selected: number | undefined
  onSelect: (index: number) => void
}

function LabeledRows({ groups, selected, onSelect }: BattleEngagementRowsProps) {
  const { t } = useTranslation('database')
  const translate: Translate = (key, options) => t(key, options)
  const sources = {
    stages: useBattleStageListSpec(),
    texts: useBattleStageListI18n(),
    facetNames: useBattleFacetNames(),
    themePacks: useThemePackListI18n(),
  }

  return groups.map((group) => (
    <SelectorGroup
      key={group.battleCase}
      label={t(`battle.case.${group.battleCase}`)}
      options={selectorEntries(
        group.rows,
        (row) => engagementLabel(row, sources, translate),
        rowContentKey,
      ).map((entry) => ({ value: entry.index, label: suffixedLabel(entry, translate) }))}
      selected={selected}
      onSelect={onSelect}
    />
  ))
}

export function BattleEngagementRows(props: BattleEngagementRowsProps) {
  if (props.groups.length === 0) return null
  return (
    <Suspense fallback={<Skeleton className="h-10 w-64" />}>
      <LabeledRows {...props} />
    </Suspense>
  )
}
