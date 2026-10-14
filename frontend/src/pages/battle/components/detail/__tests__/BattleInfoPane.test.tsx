import { describe, it, expect, beforeEach } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'

import enemy1003 from '@static/data/enemy/1003.json'
import i18n from '@/lib/i18n'
import { renderWithProviders } from '@/test-utils/renderWithProviders'
import { ENEMY_DETAIL } from '../../../hooks/useBattleDetailData'
import { EnemyDataSchema } from '../../../schemas/BattleDetailSchemas'
import { BattleInfoPane, BattlePartLabel } from '../BattleInfoPane'

const phase = EnemyDataSchema.parse(enemy1003).units['1003']?.phases[0]

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('BattleInfoPane', () => {
  it('draws the status of selected part Pearl without its name as a title', async () => {
    if (phase === undefined) throw new Error('1003 has no phase')
    const parts = phase.parts ?? []
    const pearl = parts.find((each) => each.id === 100302)
    if (pearl === undefined) throw new Error('1003 has no part 100302')
    const source = { config: ENEMY_DETAIL, id: '1003', unitId: '1003' }

    renderWithProviders(
      <>
        <div data-testid="selector-label">
          <BattlePartLabel {...source} phaseId="1003" part={pearl} />
        </div>
        <BattleInfoPane phase={phase} parts={parts} part={pearl} level={40} tier={1} />
      </>,
    )

    await waitFor(() => expect(screen.getByTestId('selector-label').textContent).toBe('Pearl'))
    const block = screen.getByTestId('battle-status-block')
    expect(within(block).getByText('520')).toBeDefined()
    expect(within(block).queryByText('Pearl')).toBeNull()
    expect(within(block).queryByText('Head')).toBeNull()
  })
})
