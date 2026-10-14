import { beforeEach, describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'

import assistant400001 from '@static/data/assistant/400001.json'
import enemy1347 from '@static/data/enemy/1347.json'
import i18n from '@/lib/i18n'
import { SkillIdSchema } from '@/shared/gameData'
import { renderWithProviders } from '@/test-utils/renderWithProviders'
import { ASSISTANT_DETAIL, ENEMY_DETAIL } from '../../../hooks/useBattleDetailData'
import {
  AssistantDataSchema,
  EnemyDataSchema,
  type BattlePhase,
} from '../../../schemas/BattleDetailSchemas'
import { BattleSkillCard, type BattleTextSource } from '../BattleSkillCard'

const XICHUN_LEVEL = 60
const XICHUN_SOURCE = {
  config: ASSISTANT_DETAIL,
  id: '400001',
  unitId: '400005',
  phaseId: '400005',
}
const DESTROY_LEVEL = 85
const DESTROY_SOURCE = { config: ENEMY_DETAIL, id: '1347', unitId: '1347', phaseId: '1347' }

const xichun = AssistantDataSchema.parse(assistant400001).units['400005']?.phases[0]
const destroyer = EnemyDataSchema.parse(enemy1347).units['1347']?.phases[0]

beforeEach(async () => {
  await i18n.changeLanguage('KR')
})

function renderSkill(
  phase: BattlePhase | undefined,
  source: BattleTextSource,
  skillId: string,
  tier: number,
  unitLevel: number,
) {
  const entry = phase?.skills[skillId]
  if (phase === undefined || entry === undefined) throw new Error(`no skill ${skillId}`)
  return renderWithProviders(
    <BattleSkillCard
      source={source}
      imageOwnerId={undefined}
      skillId={SkillIdSchema.parse(skillId)}
      entry={entry}
      tier={tier}
      palette="player"
      importancePosition="unlock"
      unitLevel={unitLevel}
      skillLevelAdders={phase.skillLevelAdders}
    />,
  )
}

function skillLevel(container: HTMLElement): string | null | undefined {
  return container.querySelector('[data-slot="skill-level"]')?.textContent
}

describe('skill level adders', () => {
  it('reads unit level + 7 on 40000101 and unit level + 10 on 40000103 of assist 400005', () => {
    const strike = renderSkill(xichun, XICHUN_SOURCE, '40000101', 5, XICHUN_LEVEL)
    expect(skillLevel(strike.container)).toBe(String(XICHUN_LEVEL + 7))
    strike.unmount()
    const spring = renderSkill(xichun, XICHUN_SOURCE, '40000103', 5, XICHUN_LEVEL)
    expect(skillLevel(spring.container)).toBe(String(XICHUN_LEVEL + 10))
  })

  it('reads unit level + 4 on evade skill 40000104 of assist 400005', () => {
    const { container } = renderSkill(xichun, XICHUN_SOURCE, '40000104', 5, XICHUN_LEVEL)
    expect(skillLevel(container)).toBe(String(XICHUN_LEVEL + 4))
  })

  it('reads unit level + skill level correction on enemy 1347 skill 134707', () => {
    const { container } = renderSkill(destroyer, DESTROY_SOURCE, '134707', 1, DESTROY_LEVEL)
    expect(skillLevel(container)).toBe(String(DESTROY_LEVEL + 3))
  })
})

describe('level names', () => {
  it.each([
    ['40000101', 2, '적격 2형 - 파(破)'],
    ['40000101', 5, '적격 5형 - 진(進)'],
    ['40000104', 4, '수회유환'],
    ['40000104', 5, '수회유환'],
  ] as const)('titles %s at skill level %i as %s', async (skillId, tier, name) => {
    const { container } = renderSkill(xichun, XICHUN_SOURCE, skillId, tier, XICHUN_LEVEL)
    expect(await screen.findByText(name)).toBeDefined()
    expect(container.querySelector('[data-slot="name-plate"]')?.textContent).toBe(name)
  })

  it('titles enemy skill 134707, whose descs carry no name, by its skill name', async () => {
    await i18n.changeLanguage('EN')
    const { container } = renderSkill(destroyer, DESTROY_SOURCE, '134707', 1, DESTROY_LEVEL)
    expect(await screen.findByText('Destroy the Sound, Crush Flat the Thought')).toBeDefined()
    expect(container.querySelector('[data-slot="name-plate"]')?.textContent).toBe(
      'Destroy the Sound, Crush Flat the Thought',
    )
  })
})

describe('acquire line', () => {
  it.each([
    ['KR', '획득 조건: 지(智) 800 또는 지(智) 500, 용(勇) 500'],
    ['EN', 'Acquired: Zhi (智) 800 or Zhi (智) 500, Yong (勇) 500'],
  ] as const)('shows the %s acquire conditions of 40000501', async (language, line) => {
    await i18n.changeLanguage(language)
    renderSkill(xichun, XICHUN_SOURCE, '40000501', 5, XICHUN_LEVEL)
    expect(await screen.findByText(line)).toBeDefined()
  })

  it('shows no acquire line on 40000101', async () => {
    const { container } = renderSkill(xichun, XICHUN_SOURCE, '40000101', 5, XICHUN_LEVEL)
    expect(await screen.findByText('적격 5형 - 진(進)')).toBeDefined()
    expect(container.querySelector('[data-slot="skill-acquire"]')).toBeNull()
  })
})
