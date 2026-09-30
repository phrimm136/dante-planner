import { beforeEach, describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { renderWithProviders } from '@/test-utils/renderWithProviders'
import { ENEMY_DETAIL } from '../../../hooks/useBattleDetailData'
import { BattlePassiveCard } from '../BattlePassiveCard'

const source = { config: ENEMY_DETAIL, id: '1322', unitId: '1322', phaseId: '1322' }

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('BattlePassiveCard', () => {
  it('sets the flavor of passive 132208 at 33 units with 47 above and 35 below', async () => {
    renderWithProviders(
      <BattlePassiveCard
        source={source}
        passiveId="132208"
        isLocked={false}
        importanceLevel={0}
        palette="enemy"
      />,
    )
    const flavor = await screen.findByTestId('passive-flavor')
    expect(flavor.style.fontSize).toBe('0.7219rem')
    expect(flavor.style.marginTop).toBe('1.0281rem')
    expect(flavor.style.marginBottom).toBe('0.7656rem')
    expect(flavor.style.paddingLeft).toBe('0.5906rem')
    expect(flavor.style.paddingRight).toBe('0.4375rem')
    expect(flavor.classList).not.toContain('text-sm')
    expect(flavor.classList).not.toContain('pt-1')
  })

  it('draws the name of passive 132208 on the passive plate', async () => {
    const { container } = renderWithProviders(
      <BattlePassiveCard
        source={source}
        passiveId="132208"
        isLocked={false}
        importanceLevel={0}
        palette="enemy"
      />,
    )
    await screen.findByTestId('passive-flavor')
    const plate = container.querySelector<HTMLElement>('[data-slot="name-plate"] > div')
    expect(plate?.style.height).toBe('1.5912rem')
    expect(plate?.style.paddingLeft).toBe('0.6563rem')
    expect(plate?.style.paddingRight).toBe('2.1875rem')
    expect(plate?.querySelector('span')?.style.fontSize).toBe('0.875rem')
  })
})
