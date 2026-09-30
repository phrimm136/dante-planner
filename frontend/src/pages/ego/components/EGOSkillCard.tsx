import { useEGODetailI18n } from '../hooks/useEGODetailData'
import {
  EGOSkillCard,
  SkillDescription,
  getMergedSkillDesc,
  selectSkillLevel,
} from '@/shared/skill'
import { NamePlate } from '@/shared/gameText'
import type { Threadspin } from '../types/EGOTypes'
import { IDENTITY_LEVEL_CAP } from '@/shared/gameData'
import type { SkillEntry, SkillId } from '@/shared/gameData'

interface EGOSkillCardWithGranularI18nProps {
  egoId: string
  skillId: SkillId
  entry: SkillEntry
  threadspin: Threadspin
}

export function EGOSkillCardWithGranularI18n({
  egoId,
  skillId,
  entry,
  threadspin,
}: EGOSkillCardWithGranularI18nProps) {
  const { level } = selectSkillLevel(entry.levels, threadspin)

  return (
    <EGOSkillCard
      egoId={egoId}
      skillId={skillId}
      entry={entry}
      level={level}
      unitLevel={IDENTITY_LEVEL_CAP}
      renderName={(attributeType) => (
        <EGOSkillName egoId={egoId} skillId={skillId} attributeType={attributeType} />
      )}
      descriptionSlot={<EGOSkillDescription egoId={egoId} skillId={skillId} level={threadspin} />}
      importanceLevel={entry.importanceLevel}
      palette="player"
    />
  )
}

function EGOSkillName({
  egoId,
  skillId,
  attributeType,
}: {
  egoId: string
  skillId: SkillId
  attributeType?: string | undefined
}) {
  const i18n = useEGODetailI18n(egoId)
  return (
    <NamePlate
      kind="skill"
      name={i18n.skills[skillId]?.name ?? ''}
      {...(attributeType !== undefined && { attributeType })}
    />
  )
}

function EGOSkillDescription({
  egoId,
  skillId,
  level,
}: {
  egoId: string
  skillId: SkillId
  level: Threadspin
}) {
  const i18n = useEGODetailI18n(egoId)
  const skillI18n = i18n.skills[skillId]
  return (
    <SkillDescription
      descData={getMergedSkillDesc(skillI18n?.descs ?? [], level)}
      {...(skillI18n?.flavor !== undefined && { flavor: skillI18n.flavor })}
    />
  )
}
