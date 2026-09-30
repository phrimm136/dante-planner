import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import enemy1347 from '@static/data/enemy/1347.json'
import { SkillEntrySchema } from '@/shared/gameData'
import { skillLevelAt, toSkillCardData } from '../../lib/skillLevel'
import { SkillCard } from '../SkillCard'

const [phase] = enemy1347.units['1347'].phases
const entry = SkillEntrySchema.parse(phase?.skills['134707'])
const [level] = entry.levels

function renderCard(unitLevel: number | undefined) {
  return render(
    <SkillCard
      skillId="134707"
      skillData={toSkillCardData(entry, level)}
      coins={level.coins}
      skillImagePath={undefined}
      skillTier={entry.skillTier}
      nameSlot={<span>Name</span>}
      descriptionSlot={<span>Desc</span>}
      unitLevel={unitLevel}
      importanceLevel={0}
      palette="enemy"
    />,
  )
}

function levelText(container: HTMLElement): string | null | undefined {
  return container.querySelector('[data-slot="skill-level"]')?.textContent
}

describe('skill attack level', () => {
  it('adds the skill level correction to the unit level: LV85 with +3 shows 88', () => {
    expect(levelText(renderCard(85).container)).toBe('88')
  })

  it('shows no level number when the unit level is unknown', () => {
    const { container } = renderCard(undefined)
    expect(container.querySelector('[data-slot="skill-level"]')).toBeNull()
    expect(container.querySelector('[data-slot="skill-level-icon"]')).not.toBeNull()
  })

  it('floors a negative correction at level 1', () => {
    expect(skillLevelAt(2, -5)).toBe(1)
    expect(skillLevelAt(60, -5)).toBe(55)
  })

  it('draws the level with a hard offset underlay', () => {
    const style = (
      renderCard(85).container.querySelector('[data-slot="skill-level"]') as HTMLElement
    ).style
    expect(style.textShadow).toMatch(/#040001$/)
  })
})

describe('skill coin numbers', () => {
  it('rings the coin scale and base power in black instead of a hairline stroke', () => {
    const { container } = renderCard(85)
    for (const slot of ['coin-scale', 'base-power']) {
      const element = container.querySelector<HTMLElement>(`[data-slot="${slot}"]`)
      expect(element?.style.textShadow).toMatch(/#000000$/)
      expect(element?.className).not.toMatch(/text-stroke/)
    }
  })
})
