import { EGOSkillImageComposite } from './EGOSkillImageComposite'
import { EGOSkillInfoPanel } from './EGOSkillInfoPanel'
import { SkillDescription } from '@/components/identity/SkillDescription'
import { SkillCardLayout } from '@/components/common/SkillCardLayout'
import type { EGOSkillData, EGOSkillI18n } from '@/types/EGOTypes'

interface EGOSkillCardProps {
  egoId: string
  skillType: 'awakening' | 'corrosion'
  skillData: EGOSkillData
  skillI18n: EGOSkillI18n
  sin: string
  threadspin: '3' | '4'
}

/**
 * EGOSkillCard - Complete EGO skill display card
 *
 * Layout:
 * - Horizontal layout with skill image on left, info panel on right
 * - Skill description below the main layout
 */
export function EGOSkillCard({
  egoId,
  skillType,
  skillData,
  skillI18n,
  sin,
  threadspin,
}: EGOSkillCardProps) {
  const skillName = skillI18n.name
  const skillThreadspinI18n = skillI18n.threadspins[threadspin][0]

  return (
    <SkillCardLayout
      imageComposite={
        <EGOSkillImageComposite
          egoId={egoId}
          skillType={skillType}
          skillData={skillData}
          sin={sin}
          threadspin={threadspin}
        />
      }
      infoPanel={
        <EGOSkillInfoPanel
          skillName={skillName}
          skillData={skillData}
          threadspin={threadspin}
        />
      }
      description={<SkillDescription uptieI18nData={skillThreadspinI18n} />}
    />
  )
}
