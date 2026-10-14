import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'

import { EGOPassivesPane, IdentityPassivesPane, PassivePlaceholder } from '@/shared/passive'
import { PassiveIdSchema } from '@/shared/gameData'

const INFO_LEFT = '0.5906rem'
const INFO_RIGHT = '0.4375rem'

const insets = (element: HTMLElement) => [element.style.paddingLeft, element.style.paddingRight]

const toIds = (list: string[][]) => list.map((tier) => tier.map((id) => PassiveIdSchema.parse(id)))

describe('PassivePlaceholder', () => {
  it('insets its text like the passive info line', () => {
    render(<PassivePlaceholder>No passives</PassivePlaceholder>)

    expect(insets(screen.getByText('No passives'))).toEqual([INFO_LEFT, INFO_RIGHT])
  })

  it('stands in for an empty E.G.O passive list', () => {
    render(
      <EGOPassivesPane
        passives={{ passiveList: [] }}
        threadspinLevel={4}
        renderPassive={() => null}
      />,
    )

    expect(insets(screen.getByText('No passives'))).toEqual([INFO_LEFT, INFO_RIGHT])
  })

  it.each([
    ['battle', 'No battle passives', [], [['1010121']]],
    ['support', 'No support passives', [['1010101']], []],
  ])('stands in for an empty identity %s passive list', (_name, text, battle, support) => {
    render(
      <IdentityPassivesPane
        passives={{ battlePassiveList: toIds(battle), supportPassiveList: toIds(support) }}
        uptieLevel={4}
        renderPassive={(id) => <span key={id}>{id}</span>}
      />,
    )

    expect(insets(screen.getByText(text))).toEqual([INFO_LEFT, INFO_RIGHT])
  })
})
