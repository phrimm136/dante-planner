import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import enemy1339 from '@static/data/enemy/1339.json'
import { getCoinSpritePath, getImportanceIconPath } from '@/shared/assets'
import { SkillEntrySchema, SkillIdSchema } from '@/shared/gameData'
import { IdentitySkillCard } from '../IdentitySkillCard'

const [phase] = enemy1339.phases
const entry = SkillEntrySchema.parse(phase?.skills['133907'])
const skillId = SkillIdSchema.parse('133907')

describe('skill core decoration from enemy 1339 skill 133907', () => {
  it('passes the level coins and the importance level through the core', () => {
    const { container } = render(
      <IdentitySkillCard
        identityId="1339"
        skillId={skillId}
        entry={entry}
        level={entry.levels[0]}
        renderName={() => <span>Name</span>}
        descriptionSlot={<span>Desc</span>}
        importanceLevel={entry.importanceLevel}
        palette="enemy"
      />,
    )
    const green = getCoinSpritePath('GREEN')
    const coinSources = Array.from(container.querySelectorAll('img'))
      .map((img) => img.getAttribute('src'))
      .filter((src) => src === green)
    expect(coinSources).toEqual([green, green])

    expect(entry.importanceLevel).toBe(2)
    expect(
      container.querySelector('[data-slot="importance-icon-image"]')?.getAttribute('src'),
    ).toBe(getImportanceIconPath('enemy', '2'))
    expect(container.querySelector('[data-slot="importance-glow"]')).not.toBeNull()
  })

  it('renders the undecorated layout at importance 0', () => {
    const { container } = render(
      <IdentitySkillCard
        identityId="1339"
        skillId={skillId}
        entry={entry}
        level={entry.levels[0]}
        renderName={() => <span>Name</span>}
        descriptionSlot={<span>Desc</span>}
        importanceLevel={0}
        palette="enemy"
      />,
    )
    expect(container.querySelector('[data-slot="importance-decoration"]')).toBeNull()
    expect(container.firstElementChild?.getAttribute('data-skill-id')).toBe('133907')
    expect(container.firstElementChild?.classList.contains('@container')).toBe(true)
  })
})
