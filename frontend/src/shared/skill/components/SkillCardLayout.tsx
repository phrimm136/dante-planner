import { GAME_TEXT_CLASS } from '@/lib/constants'
import { cn } from '@/lib/utils'
import { skillLowerLayout, skillUpperLayout } from '../lib/skillLayout'

interface SkillCardLayoutProps {
  skillId: string
  hasImportance: boolean
  imageComposite: React.ReactNode
  infoPanel: React.ReactNode
  description: React.ReactNode
}

const UPPER = skillUpperLayout()

export function SkillCardLayout({
  skillId,
  hasImportance,
  imageComposite,
  infoPanel,
  description,
}: SkillCardLayoutProps) {
  const lower = skillLowerLayout(hasImportance)

  return (
    <div
      className={cn('@container', GAME_TEXT_CLASS)}
      data-skill-id={skillId}
      style={{ ...UPPER.card, ...lower.card }}
    >
      <div data-slot="skill-upper" className="relative" style={UPPER.block}>
        {imageComposite}
        {infoPanel}
      </div>

      <div data-slot="skill-lower" style={lower.block}>
        {description}
      </div>
    </div>
  )
}
