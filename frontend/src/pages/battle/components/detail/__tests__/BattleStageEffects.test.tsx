import { describe, it, expect, beforeEach } from 'vitest'
import { waitFor } from '@testing-library/react'

import enemy8390 from '@static/data/enemy/8390.json'
import i18n from '@/lib/i18n'
import { renderWithProviders } from '@/test-utils/renderWithProviders'
import { ENEMY_DETAIL } from '../../../hooks/useBattleDetailData'
import { EnemyDataSchema, type BuffGrant } from '../../../schemas/BattleDetailSchemas'
import { BattleStageEffects } from '../BattleStageEffects'

const [baseRow] = EnemyDataSchema.parse(enemy8390).engagements

function stageGrant(keywordId: string, derivable: boolean): BuffGrant {
  return {
    buffId: 1,
    keywordId,
    target: 'enemy',
    stack: 1,
    derivable,
    source: { kind: 'stageScript', class: 'StageScript_Test' },
  }
}

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('BattleStageEffects', () => {
  it('shows only the grant no spawned unit explains', async () => {
    if (baseRow === undefined) throw new Error('8390 has no row')
    const row = {
      ...baseRow,
      facts: {
        ...baseRow.facts,
        buffGrants: [stageGrant('FragmentOfHope', true), stageGrant('UnfinishedDream', false)],
      },
    }
    const { container } = renderWithProviders(
      <BattleStageEffects
        source={{ config: ENEMY_DETAIL, id: '8390', unitId: '8390', phaseId: '8390' }}
        row={row}
        own={{ unitIds: new Set(), partIds: new Set() }}
        phaseIds={[]}
      />,
    )

    await waitFor(() =>
      expect(container.querySelector('[data-testid="battle-stage-effects"]')).not.toBeNull(),
    )
    expect(
      [...container.querySelectorAll('[data-keyword-id]')].map((card) =>
        card.getAttribute('data-keyword-id'),
      ),
    ).toEqual(['UnfinishedDream'])
  })
})
