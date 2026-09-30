import {
  SkillCard,
  SkillDescription,
  getMergedSkillDesc,
  selectSkillLevel,
  toSkillCardData,
} from '@/shared/skill'
import { getBattleSkillIconPath, getSkillImagePath } from '@/shared/assets'
import { NamePlate } from '@/shared/gameText'
import type { ImportancePalette, SkillEntry, SkillId, SkillLevel } from '@/shared/gameData'
import { useBattleDetailI18n, type BattleI18nConfig } from '../../hooks/useBattleDetailData'
import { phaseTexts } from '../../lib/battleDetail'

export interface BattleTextSource {
  config: BattleI18nConfig
  id: string
  unitId: string
  phaseId: string
}

interface BattleSkillCardProps {
  source: BattleTextSource
  imageOwnerId: string | undefined
  skillId: SkillId
  entry: SkillEntry
  tier: number
  palette: ImportancePalette
  importancePosition: 'unlock' | 'base'
  unitLevel: number | undefined
}

function battleSkillImagePath(
  level: SkillLevel,
  imageOwnerId: string | undefined,
  skillId: SkillId,
): string {
  if (level.iconID) return getBattleSkillIconPath(level.iconID)
  if (imageOwnerId !== undefined) return getSkillImagePath(imageOwnerId, skillId)
  return getBattleSkillIconPath(skillId)
}

export function BattleSkillCard({
  source,
  imageOwnerId,
  skillId,
  entry,
  tier,
  palette,
  importancePosition,
  unitLevel,
}: BattleSkillCardProps) {
  const { level, isLocked } = selectSkillLevel(entry.levels, tier)
  const descriptionLevel = isLocked ? level.level : tier
  const cardData = toSkillCardData(entry, level)

  return (
    <SkillCard
      skillId={skillId}
      skillData={cardData}
      coins={level.coins}
      skillImagePath={battleSkillImagePath(level, imageOwnerId, skillId)}
      skillTier={entry.skillTier}
      nameSlot={
        <BattleSkillName source={source} skillId={skillId} attributeType={cardData.attributeType} />
      }
      descriptionSlot={
        <BattleSkillDescription source={source} skillId={skillId} level={descriptionLevel} />
      }
      importanceLevel={entry.importanceLevel}
      palette={palette}
      importancePosition={importancePosition}
      unitLevel={unitLevel}
      isDefenseSkill={cardData.atkType === 'NONE' || !cardData.atkType}
      isLocked={isLocked}
      missingImage="frame"
    />
  )
}

function useSkillText(source: BattleTextSource, skillId: SkillId) {
  const i18n = useBattleDetailI18n(source.config, source.id)
  return phaseTexts(i18n, source.unitId, source.phaseId)?.skills?.[skillId]
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
    <NamePlate
      kind="skill"
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
  return (
    <SkillDescription
      descData={getMergedSkillDesc(text?.descs ?? [], level)}
      {...(text?.flavor !== undefined && { flavor: text.flavor })}
    />
  )
}
