import { beforeEach, describe, expect, it, vi } from 'vitest'
import { waitFor } from '@testing-library/react'

import panicInfoEN from '@static/i18n/EN/panicInfo.json'
import i18n from '@/lib/i18n'
import { getPanicIconPath } from '@/shared/assets'
import { renderWithProviders } from '@/test-utils/renderWithProviders'
import { ENEMY_DETAIL } from '../../../hooks/useBattleDetailData'
import { BattleEntityI18nSchema } from '../../../schemas/BattleDetailSchemas'
import { BattleSanityPane } from '../BattleSanityPane'

const FLAVOR = '손이 떨리면, 가위질도 흔들려… 이대로는 재단할 수 없어…'

const i18nWith = (sanity: Record<string, string>) => ({
  units: { '1110': { phases: { '1110': { sanity } } } },
})

vi.mock(import('../../../hooks/useBattleDetailData'), async (importOriginal) => ({
  ...(await importOriginal()),
  useBattleDetailI18n: () =>
    i18nWith({ panicName: 'Tremor', panicText: 'Does not act.', panicFlavor: FLAVOR }),
}))

const source = { config: ENEMY_DETAIL, id: '1110', unitId: '1110', phaseId: '1110' }

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('BattleSanityPane', () => {
  it('shows the panic flavor of the battle sanity texts', async () => {
    const { container } = renderWithProviders(
      <BattleSanityPane source={source} sanity={{ hasSanity: true, panicType: 1110 }} />,
    )

    const flavor = await waitFor(() => {
      const found = container.querySelector('[data-slot="panic-flavor"]')
      if (found === null) throw new Error('missing panic flavor')
      return found
    })
    expect(flavor.textContent).toBe(FLAVOR)
  })

  it('draws the 9999 icon for a panic type the panic info has no entry for', async () => {
    expect(panicInfoEN).not.toHaveProperty('1100')
    const { container } = renderWithProviders(
      <BattleSanityPane source={source} sanity={{ hasSanity: true, panicType: 1100 }} />,
    )

    const icon = await waitFor(() => {
      const found = container.querySelector('[data-slot="panic-icon"]')
      if (found === null) throw new Error('missing panic icon')
      return found
    })
    expect(icon.getAttribute('src')).toBe(getPanicIconPath('9999'))
  })
})

describe('battle sanity texts schema', () => {
  it('accepts a panic flavor', () => {
    const parsed = BattleEntityI18nSchema.parse(i18nWith({ panicText: 'a', panicFlavor: FLAVOR }))
    expect(parsed.units['1110']?.phases?.['1110']?.sanity?.panicFlavor).toBe(FLAVOR)
  })

  it('rejects an unknown key', () => {
    expect(BattleEntityI18nSchema.safeParse(i18nWith({ panicFlavour: FLAVOR })).success).toBe(false)
  })
})
