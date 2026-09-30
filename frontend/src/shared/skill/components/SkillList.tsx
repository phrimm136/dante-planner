import { Fragment, type CSSProperties, type ReactNode } from 'react'

import { Separator } from '@/components/ui/separator'
import { SKILL_TAB_UNITS } from '@/lib/constants'
import { clientLength } from '@/lib/utils'
import type { SkillId } from '@/shared/gameData'

interface SkillListProps<T extends { id: SkillId }> {
  skills: readonly T[]
  renderSkill: (skill: T) => ReactNode
}

const TAB_STYLE = {
  '--importance-pane-edge': clientLength(SKILL_TAB_UNITS.EDGE),
} as CSSProperties

const SEPARATOR_STYLE: CSSProperties = {
  height: clientLength(SKILL_TAB_UNITS.LINE),
  marginBlock: clientLength(SKILL_TAB_UNITS.GAP),
}

export function SkillList<T extends { id: SkillId }>({ skills, renderSkill }: SkillListProps<T>) {
  return (
    <div className="importance-skill-pane border rounded" style={TAB_STYLE}>
      {skills.map((skill, index) => (
        <Fragment key={skill.id}>
          {index > 0 && <Separator data-slot="skill-separator" style={SEPARATOR_STYLE} />}
          {renderSkill(skill)}
        </Fragment>
      ))}
    </div>
  )
}
