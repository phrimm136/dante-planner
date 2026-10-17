import { useTranslation } from 'react-i18next'

import { PANEL_TEXT_NODES, UNIT_PANEL_COLORS } from '@/lib/constants'
import {
  SkillCard,
  SkillDescription,
  getMergedSkillDesc,
  selectSkillLevel,
  textBlockStyle,
  toSkillCardData,
} from '@/shared/skill'
import { getBattleSkillIconPath, getSkillImagePath } from '@/shared/assets'
import { NamePlate } from '@/shared/gameText'
import type { ImportancePalette, SkillId, SkillLevel } from '@/shared/gameData'
import { useBattleDetailI18n, type BattleI18nConfig } from '../../hooks/useBattleDetailData'
import {
  acquireConditionText,
  phaseTexts,
  skillLevelAdder,
  skillLevelName,
} from '../../lib/battleDetail'
import type { SkillLevelAdders } from '../../lib/battleGrowth'
import type { BattleSkillAcquire, BattleSkillEntry } from '../../schemas/BattleDetailSchemas'

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
  entry: BattleSkillEntry
  tier: number
  palette: ImportancePalette
  importancePosition: 'unlock' | 'base'
  unitLevel: number | undefined
  skillLevelAdders: SkillLevelAdders | undefined
}

const ACQUIRE_STYLE = textBlockStyle(
  PANEL_TEXT_NODES.SKILL_DESCRIPTION,
  UNIT_PANEL_COLORS.DESCRIPTION,
)

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
  skillLevelAdders,
}: BattleSkillCardProps) {
  const { level, isLocked } = selectSkillLevel(entry.levels, tier)
  const descriptionLevel = isLocked ? level.level : tier
  const shown = toSkillCardData(entry, level)
  const cardData = {
    ...shown,
    skillLevelCorrection:
      shown.skillLevelCorrection + skillLevelAdder(level.defType, skillLevelAdders),
  }

  return (
    <SkillCard
      skillId={skillId}
      skillData={cardData}
      coins={level.coins}
      skillImagePath={battleSkillImagePath(level, imageOwnerId, skillId)}
      skillTier={entry.skillTier}
      nameSlot={
        <BattleSkillName
          source={source}
          skillId={skillId}
          level={level.level}
          attributeType={cardData.attributeType}
        />
      }
      descriptionSlot={
        <BattleSkillDescription
          source={source}
          skillId={skillId}
          level={descriptionLevel}
          acquire={entry.acquire}
        />
      }
      importanceLevel={entry.importanceLevel}
      palette={palette}
      importancePosition={importancePosition}
      partTypes={entry.linkedPartTypes}
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
  level,
  attributeType,
}: {
  source: BattleTextSource
  skillId: SkillId
  level: number
  attributeType?: string | undefined
}) {
  const text = useSkillText(source, skillId)
  return (
    <NamePlate
      kind="skill"
      name={text === undefined ? '' : skillLevelName(text.descs, level, text.name)}
      {...(attributeType !== undefined && { attributeType })}
    />
  )
}

function BattleSkillDescription({
  source,
  skillId,
  level,
  acquire,
}: {
  source: BattleTextSource
  skillId: SkillId
  level: number
  acquire: BattleSkillAcquire | undefined
}) {
  const text = useSkillText(source, skillId)
  return (
    <>
      {acquire !== undefined && <BattleSkillAcquireLine source={source} acquire={acquire} />}
      <SkillDescription
        descData={getMergedSkillDesc(text?.descs ?? [], level)}
        {...(text?.flavor !== undefined && { flavor: text.flavor })}
      />
    </>
  )
}

function BattleSkillAcquireLine({
  source,
  acquire,
}: {
  source: BattleTextSource
  acquire: BattleSkillAcquire
}) {
  const { t } = useTranslation('database')
  const i18n = useBattleDetailI18n(source.config, source.id)
  const conditions = acquireConditionText(
    acquire,
    phaseTexts(i18n, source.unitId, source.phaseId)?.conditionNames,
    { and: t('battle.detail.acquireAnd'), or: t('battle.detail.acquireOr') },
  )
  return (
    <p data-slot="skill-acquire" className="text-sm" style={ACQUIRE_STYLE}>
      {t('battle.detail.acquire', { conditions })}
    </p>
  )
}
