import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { TextSkeleton } from '@/components/feedback/TextSkeleton'

import { SECTION_STYLES } from '@/lib/constants'
import { IdentityPassivesPane } from '@/shared/passive'
import type { ImportancePalette, Uptie } from '@/shared/gameData'
import { useBattleDetailI18n } from '../../hooks/useBattleDetailData'
import {
  isIdentityShaped,
  isShownPassive,
  phaseTexts,
  visiblePassives,
  withShownPassives,
} from '../../lib/battleDetail'
import type { BattlePart, BattlePhase } from '../../schemas/BattleDetailSchemas'
import { BattlePassiveCard } from './BattlePassiveCard'
import type { BattleTextSource } from './BattleSkillCard'

interface BattlePassivesPaneProps {
  source: BattleTextSource
  phase: BattlePhase
  parts: readonly BattlePart[]
  part: BattlePart | undefined
  tier: number
  palette: ImportancePalette
}

export function BattlePassivesPane(props: BattlePassivesPaneProps) {
  return (
    <Suspense
      fallback={
        <div className="border rounded p-4">
          <TextSkeleton lines={3} width="full" />
        </div>
      }
    >
      <ShownPassives {...props} />
    </Suspense>
  )
}

function ShownPassives({ source, phase, parts, part, tier, palette }: BattlePassivesPaneProps) {
  const { t } = useTranslation('database')
  const i18n = useBattleDetailI18n(source.config, source.id)
  const texts = phaseTexts(i18n, source.unitId, source.phaseId)?.passives
  const passives = phase.passives

  if (part === undefined && isIdentityShaped(passives)) {
    return (
      <IdentityPassivesPane
        passives={withShownPassives(passives, texts)}
        uptieLevel={tier as Uptie}
        renderPassive={(passiveId, isLocked) => (
          <BattlePassiveCard
            source={source}
            passiveId={passiveId}
            condition={passives.conditions[passiveId]}
            isLocked={isLocked}
            importanceLevel={0}
            palette={palette}
          />
        )}
      />
    )
  }

  const entries = visiblePassives(phase, parts, part).filter(([passiveId]) =>
    isShownPassive(texts, passiveId),
  )

  return (
    <div className="border rounded p-4 space-y-4">
      {entries.map(([passiveId, importanceLevel]) => (
        <BattlePassiveCard
          key={passiveId}
          source={source}
          passiveId={passiveId}
          isLocked={false}
          importanceLevel={importanceLevel}
          palette={palette}
        />
      ))}
      {entries.length === 0 && (
        <div className={SECTION_STYLES.TEXT.caption}>{t('passive.none')}</div>
      )}
    </div>
  )
}
