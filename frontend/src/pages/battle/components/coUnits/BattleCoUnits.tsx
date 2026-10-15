import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { Skeleton } from '@/components/ui/skeleton'
import { SECTION_STYLES } from '@/lib/constants'
import { useIdentityListI18n } from '@/pages/identity'
import { useAssistantListI18n, useAssistantListSpec } from '../../hooks/useAssistantListData'
import { useEnemyListI18n, useEnemyListSpec } from '../../hooks/useEnemyListData'
import { useSinnerEventListI18n, useSinnerEventListSpec } from '../../hooks/useSinnerEventListData'
import { BATTLE_DETAIL_ROUTES, type BattleFamily } from '../../lib/battleVocabulary'
import { coUnitChips, type CoUnitCatalog, type CoUnitChip } from '../../lib/coUnits'
import type { EngagementRow } from '../../schemas/BattleDetailSchemas'

export interface BattleCoUnitsProps {
  row: EngagementRow
  family: BattleFamily
  id: string
}

function useCoUnitCatalog(): CoUnitCatalog {
  return {
    specs: {
      enemy: useEnemyListSpec(),
      assistant: useAssistantListSpec(),
      sinnerEvent: useSinnerEventListSpec(),
    },
    names: {
      enemy: useEnemyListI18n(),
      assistant: useAssistantListI18n(),
      sinnerEvent: useSinnerEventListI18n(),
    },
    identityTitles: useIdentityListI18n(),
  }
}

function ChipLabel({ chip }: { chip: CoUnitChip }) {
  const { t } = useTranslation('database')
  if (chip.relation === undefined) return chip.label
  return t(`battle.detail.coUnits.${chip.relation}`, { name: chip.label })
}

function CoUnitLine({ row, family, id }: BattleCoUnitsProps) {
  const { t } = useTranslation('database')
  const chips = coUnitChips(row, useCoUnitCatalog(), { family, id })
  if (chips.length === 0) return null

  return (
    <div data-testid="battle-co-units" className="mt-3 space-y-2">
      <div className={SECTION_STYLES.TEXT.sectionTitle}>{t('battle.detail.coUnits.title')}</div>
      <div className="flex flex-wrap gap-1">
        {chips.map((chip) => (
          <Link
            key={chip.key}
            to={BATTLE_DETAIL_ROUTES[chip.family]}
            params={{ id: chip.id }}
            search={{ stage: chip.stage }}
            className="rounded-md border border-border px-3 py-1 text-sm hover:bg-muted"
          >
            <ChipLabel chip={chip} />
          </Link>
        ))}
      </div>
    </div>
  )
}

export function BattleCoUnits(props: BattleCoUnitsProps) {
  if (props.row.coUnits.length === 0) return null
  return (
    <Suspense fallback={<Skeleton className="mt-3 h-16 w-64" />}>
      <CoUnitLine {...props} />
    </Suspense>
  )
}
