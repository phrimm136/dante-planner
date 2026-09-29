import {
  IdentitySkillCard,
  SkillDescription,
  getMergedSkillDesc,
  selectSkillLevel,
} from '@/shared/skill'
import { StyledSkillName } from '@/shared/gameText'
import type { ImportancePalette, SkillEntry, SkillId } from '@/shared/gameData'
import { useBattleDetailI18n, type BattleI18nConfig } from '../../hooks/useBattleDetailData'

export interface BattleTextSource {
  config: BattleI18nConfig
  id: string
  phaseId: string
}

interface BattleSkillCardProps {
  source: BattleTextSource
  imageOwnerId: string | undefined
  skillId: SkillId
  entry: SkillEntry
  tier: number
  palette: ImportancePalette
}

export function BattleSkillCard({
  source,
  imageOwnerId,
  skillId,
  entry,
  tier,
  palette,
}: BattleSkillCardProps) {
  const { level, isLocked } = selectSkillLevel(entry.levels, tier)
  const descriptionLevel = isLocked ? level.level : tier

  return (
    <IdentitySkillCard
      identityId={imageOwnerId}
      skillId={skillId}
      entry={entry}
      level={level}
      renderName={(attributeType) => (
        <BattleSkillName source={source} skillId={skillId} attributeType={attributeType} />
      )}
      descriptionSlot={
        <BattleSkillDescription source={source} skillId={skillId} level={descriptionLevel} />
      }
      isLocked={isLocked}
      importanceLevel={entry.importanceLevel}
      palette={palette}
    />
  )
}

function useSkillText(source: BattleTextSource, skillId: SkillId) {
  const i18n = useBattleDetailI18n(source.config, source.id)
  return i18n.phases?.[source.phaseId]?.skills?.[skillId]
}

function BattleSkillName({
  source,
  skillId,
  attributeType,
}: {
  source: BattleTextSource
  skillId: SkillId
  attributeType?: string | undefined
}) {
  const text = useSkillText(source, skillId)
  return (
    <StyledSkillName
      name={text?.name ?? skillId}
      {...(attributeType !== undefined && { attributeType })}
    />
  )
}

function BattleSkillDescription({
  source,
  skillId,
  level,
}: {
  source: BattleTextSource
  skillId: SkillId
  level: number
}) {
  const text = useSkillText(source, skillId)
  return <SkillDescription descData={getMergedSkillDesc(text?.descs ?? [], level)} />
}
