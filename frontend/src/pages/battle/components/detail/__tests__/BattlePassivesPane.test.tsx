import { beforeEach, describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { renderWithProviders } from '@/test-utils/renderWithProviders'
import { ENEMY_DETAIL } from '../../../hooks/useBattleDetailData'
import type { BattlePhase } from '../../../schemas/BattleDetailSchemas'
import { BattlePassivesPane } from '../BattlePassivesPane'

const source = { config: ENEMY_DETAIL, id: '1322', unitId: '1322', phaseId: '1322' }

const phaseWithoutPassives: BattlePhase = {
  id: 1322,
  spriteId: '1322',
  hp: { defaultStat: 1, incrementByLevel: 0 },
  battleKeywordList: [],
  skills: {},
  passives: {},
  sanity: { hasSanity: false },
}

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('BattlePassivesPane', () => {
  it('insets the empty passive line like the passive info line', async () => {
    renderWithProviders(
      <BattlePassivesPane
        source={source}
        phase={phaseWithoutPassives}
        parts={[]}
        part={undefined}
        tier={1}
        palette="enemy"
      />,
    )

    const placeholder = await screen.findByText('No passives')
    expect(placeholder.style.paddingLeft).toBe('0.5906rem')
    expect(placeholder.style.paddingRight).toBe('0.4375rem')
  })
})
