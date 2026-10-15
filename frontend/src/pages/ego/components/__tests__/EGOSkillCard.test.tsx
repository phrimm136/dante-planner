import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'

import { SkillEntrySchema, SkillIdSchema } from '@/shared/gameData'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { EGOSkillCardWithGranularI18n } from '../EGOSkillCard'

vi.mock('../../hooks/useEGODetailData', () => ({
  useEGODetailI18n: () => ({ skills: {} }),
}))

const level = {
  level: 1,
  defType: 'ATTACK',
  attributeType: 'CRIMSON',
  atkType: 'SLASH',
  defaultValue: 3,
  targetNum: 1,
  skillLevelCorrection: 0,
  coins: [],
  coinString: 'C',
}

const entry = SkillEntrySchema.parse({
  skillTier: 3,
  skillType: 'AWAKEN',
  importanceLevel: 0,
  mpUsage: 0,
  isArea: false,
  levels: [level, level, level, level],
})

describe('EGOSkillCardWithGranularI18n', () => {
  it('shows the offense level at the season level cap plus the correction', () => {
    const { container } = render(
      <QueryClientProvider client={createTestQueryClient()}>
        <EGOSkillCardWithGranularI18n
          egoId="20102"
          skillId={SkillIdSchema.parse('2010211')}
          entry={entry}
          threadspin={4}
        />
      </QueryClientProvider>,
    )

    expect(container.querySelector('[data-slot="skill-level"]')?.textContent).toBe('65')
  })
})
