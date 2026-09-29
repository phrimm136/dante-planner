import { useTranslation } from 'react-i18next'

import { SECTION_STYLES } from '@/lib/constants'
import { IdentityPassivesPane } from '@/shared/passive'
import type { ImportancePalette, Uptie } from '@/shared/gameData'
import { isIdentityShaped, visiblePassives } from '../../lib/battleDetail'
import type { BattlePart, BattlePhase } from '../../schemas/BattleDetailSchemas'
import { BattlePassiveCard } from './BattlePassiveCard'
import type { BattleTextSource } from './BattleSkillCard'

interface BattlePassivesPaneProps {
  source: BattleTextSource
  phase: BattlePhase
  part: BattlePart | undefined
  tier: number
  palette: ImportancePalette
}

export function BattlePassivesPane({
  source,
  phase,
  part,
  tier,
  palette,
}: BattlePassivesPaneProps) {
  const { t } = useTranslation('database')
  const passives = phase.passives

  if (part === undefined && isIdentityShaped(passives)) {
    return (
      <IdentityPassivesPane
        passives={passives}
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

  const entries = visiblePassives(phase, part)

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
