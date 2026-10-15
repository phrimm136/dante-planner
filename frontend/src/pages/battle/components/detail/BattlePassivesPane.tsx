import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { TextSkeleton } from '@/components/feedback/TextSkeleton'

import { IdentityPassivesPane, PassivePlaceholder, PassiveRun, PassiveTab } from '@/shared/passive'
import type { ImportancePalette, Uptie } from '@/shared/gameData'
import { useBattleDetailI18n } from '../../hooks/useBattleDetailData'
import {
  isIdentityShaped,
  isShownPassive,
  phaseTexts,
  visiblePassives,
  withShownPassives,
} from '../../lib/battleDetail'
import type {
  BattlePart,
  BattlePhase,
  BattleUnlock,
  IdentityShapedPassives,
} from '../../schemas/BattleDetailSchemas'
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

function passiveUnlock(
  passives: IdentityShapedPassives,
  passiveId: string,
): BattleUnlock | undefined {
  const byTier: Record<string, BattleUnlock>[] = Object.values(passives.unlock ?? {})
  return byTier.find((byId) => passiveId in byId)?.[passiveId]
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
            unlock={passiveUnlock(passives, passiveId)}
          />
        )}
      />
    )
  }

  const entries = visiblePassives(phase, parts, part).filter(([passiveId]) =>
    isShownPassive(texts, passiveId),
  )
  const importanceOf = new Map(entries)

  return (
    <PassiveTab>
      <PassiveRun
        passives={entries.map(([id]) => ({ id, isLocked: false }))}
        renderPassive={(passiveId, isLocked) => (
          <BattlePassiveCard
            source={source}
            passiveId={passiveId}
            isLocked={isLocked}
            importanceLevel={importanceOf.get(passiveId) ?? 0}
            palette={palette}
          />
        )}
      />
      {entries.length === 0 && <PassivePlaceholder>{t('passive.none')}</PassivePlaceholder>}
    </PassiveTab>
  )
}
