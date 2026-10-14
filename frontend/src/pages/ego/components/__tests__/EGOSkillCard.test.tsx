import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'

import { SkillIdSchema } from '@/shared/gameData'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { EGOSkillCardWithGranularI18n } from '../EGOSkillCard'
import type { EGOSkillEntry } from '../../types/EGOTypes'

vi.mock('../../hooks/useEGODetailData', () => ({
  useEGODetailI18n: () => ({ skills: {} }),
}))

const skillEntry: EGOSkillEntry = {
  id: SkillIdSchema.parse('2010211'),
  skillData: [
    { attributeType: 'CRIMSON', atkType: 'SLASH', skillLevelCorrection: 0, coinString: 'C' },
    {},
    {},
    {},
  ],
}

describe('EGOSkillCardWithGranularI18n', () => {
  it('shows the offense level at the season level cap plus the correction', () => {
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <EGOSkillCardWithGranularI18n
          egoId="20102"
          skillType="awaken"
          skillEntry={skillEntry}
          threadspin={4}
        />
      </QueryClientProvider>,
    )

    expect(screen.getByAltText('Attack').nextElementSibling?.textContent).toBe('65')
  })
})
