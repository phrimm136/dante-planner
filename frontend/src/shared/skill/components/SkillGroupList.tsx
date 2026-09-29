import { Fragment, type ReactNode } from 'react'

import { Separator } from '@/components/ui/separator'
import type { OrderedSkill } from '../lib/skillOrder'

interface SkillGroupListProps {
  skills: readonly OrderedSkill[]
  renderSkill: (skill: OrderedSkill) => ReactNode
}

export function SkillGroupList({ skills, renderSkill }: SkillGroupListProps) {
  return (
    <div className="border rounded">
      {skills.map((skill, index) => (
        <Fragment key={skill.id}>
          {index > 0 && skills[index - 1]?.group !== skill.group && (
            <Separator data-slot="skill-group-separator" />
          )}
          {renderSkill(skill)}
        </Fragment>
      ))}
    </div>
  )
}
