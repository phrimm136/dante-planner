import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { SkillsSectionI18n } from './IdentitySkillI18n'
import { SkillTabButton } from './SkillTabButton'
import { getSkillAttributeType, getSkillSlotNumber } from '../lib/identitySkillSlots'

import type { SkillSlot } from '../lib/identitySkillSlots'
import type { IdentityData, Uptie } from '../types/IdentityTypes'

const SKILL3_UNLOCK_UPTIE = 3

interface IdentitySkillsPaneProps {
  id: string
  skills: IdentityData['skills']
  uptieLevel: Uptie
}

export function IdentitySkillsPane({ id, skills, uptieLevel }: IdentitySkillsPaneProps) {
  const { t } = useTranslation(['database', 'common'])
  const [activeSkillSlot, setActiveSkillSlot] = useState<SkillSlot>('skill1')

  const isSkill3Locked = uptieLevel < SKILL3_UNLOCK_UPTIE

  return (
    <div className="space-y-4">
      <div className="flex gap-2">
        <SkillTabButton
          attributeType={getSkillAttributeType(skills, 'skill1')}
          label={t('skill.skill1')}
          onClick={() => {
            setActiveSkillSlot('skill1')
          }}
          isActive={activeSkillSlot === 'skill1'}
        />
        <SkillTabButton
          attributeType={getSkillAttributeType(skills, 'skill2')}
          label={t('skill.skill2')}
          onClick={() => {
            setActiveSkillSlot('skill2')
          }}
          isActive={activeSkillSlot === 'skill2'}
        />
        <SkillTabButton
          attributeType={getSkillAttributeType(skills, 'skill3')}
          label={t('skill.skill3')}
          onClick={() => {
            setActiveSkillSlot('skill3')
          }}
          isActive={activeSkillSlot === 'skill3'}
          isLocked={isSkill3Locked}
        />
        <SkillTabButton
          attributeType={getSkillAttributeType(skills, 'skillDef')}
          label={t('skill.defense')}
          onClick={() => {
            setActiveSkillSlot('skillDef')
          }}
          isActive={activeSkillSlot === 'skillDef'}
        />
      </div>

      <SkillsSectionI18n
        id={id}
        skills={skills}
        activeSkillSlot={activeSkillSlot}
        uptieLevel={uptieLevel}
        getSkillSlotNumber={getSkillSlotNumber}
      />
    </div>
  )
}
