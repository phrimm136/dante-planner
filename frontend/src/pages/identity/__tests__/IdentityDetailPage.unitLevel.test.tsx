import { describe, it, expect, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { IDENTITY_LEVEL_CAP } from '@/shared/gameData'
import { mountRoute } from '@/test-utils/mountRoute'
import IdentityDetailPage from '../IdentityDetailPage'

const LOAD_WAIT_MS = 5000
const TEST_BUDGET_MS = 20000

const UNCORRECTED_SKILL_ID = '1010101'
const DEFENSE_SKILL_ID = '1010104'
const DEFENSE_CORRECTION = -2

function attackLevel(skillId: string): string | null | undefined {
  return document.querySelector(`[data-skill-id="${skillId}"] [data-slot="skill-level"]`)
    ?.textContent
}

async function mountAndSettle(): Promise<HTMLElement> {
  mountRoute('/identity/$id', '/identity/10101', IdentityDetailPage)
  await waitFor(() => expect(attackLevel(DEFENSE_SKILL_ID)).toBeDefined(), {
    timeout: LOAD_WAIT_MS,
  })
  return screen.getByRole('slider')
}

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('identity skill attack level follows the page level', () => {
  it(
    'reads the maximum level plus the correction by default',
    async () => {
      await mountAndSettle()

      expect(attackLevel(UNCORRECTED_SKILL_ID)).toBe(String(IDENTITY_LEVEL_CAP))
      expect(attackLevel(DEFENSE_SKILL_ID)).toBe(String(IDENTITY_LEVEL_CAP + DEFENSE_CORRECTION))
    },
    TEST_BUDGET_MS,
  )

  it(
    'reads 40 plus the correction at level 40',
    async () => {
      const slider = await mountAndSettle()

      fireEvent.keyDown(slider, { key: 'Home' })
      for (let step = 0; step < 3; step++) fireEvent.keyDown(slider, { key: 'PageUp' })
      for (let step = 0; step < 9; step++) fireEvent.keyDown(slider, { key: 'ArrowRight' })

      await waitFor(() => expect(screen.getByText('Lv. 40')).toBeDefined())
      expect(attackLevel(UNCORRECTED_SKILL_ID)).toBe('40')
      expect(attackLevel(DEFENSE_SKILL_ID)).toBe(String(40 + DEFENSE_CORRECTION))
    },
    TEST_BUDGET_MS,
  )

  it(
    'clamps a negative correction to 1 at level 1',
    async () => {
      const slider = await mountAndSettle()

      fireEvent.keyDown(slider, { key: 'Home' })

      await waitFor(() => expect(screen.getByText('Lv. 1')).toBeDefined())
      expect(attackLevel(UNCORRECTED_SKILL_ID)).toBe('1')
      expect(attackLevel(DEFENSE_SKILL_ID)).toBe('1')
    },
    TEST_BUDGET_MS,
  )
})
