import { useEGODetailI18n } from '../hooks/useEGODetailData'
import { EGOSkillCard, SkillDescription, getMergedSkillDesc } from '@/shared/skill'
import { StyledSkillName } from '@/shared/gameText'
import type { EGOSkillEntry, Threadspin } from '../types/EGOTypes'
import { IDENTITY_LEVEL_CAP } from '@/shared/gameData'
import type { SkillId } from '@/shared/gameData'

interface EGOSkillCardWithGranularI18nProps {
  egoId: string
  skillType: 'awaken' | 'erosion'
  skillEntry: EGOSkillEntry
  threadspin: Threadspin
}

export function EGOSkillCardWithGranularI18n({
  egoId,
  skillType,
  skillEntry,
  threadspin,
}: EGOSkillCardWithGranularI18nProps) {
  return (
    <EGOSkillCard
      egoId={egoId}
      skillType={skillType}
      skillEntry={skillEntry}
      threadspin={threadspin}
      level={IDENTITY_LEVEL_CAP}
      renderName={(attributeType) => (
        <EGOSkillName egoId={egoId} skillId={skillEntry.id} attributeType={attributeType} />
      )}
      descriptionSlot={
        <EGOSkillDescription egoId={egoId} skillId={skillEntry.id} level={threadspin} />
      }
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
    <StyledSkillName
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
