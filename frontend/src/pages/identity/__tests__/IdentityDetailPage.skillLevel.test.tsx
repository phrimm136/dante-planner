import { describe, it, expect, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { getSkillFramePath, getSkillImagePath, getSkillImagePathFromIconID } from '@/shared/assets'
import { mountRoute } from '@/test-utils/mountRoute'
import IdentityDetailPage from '../IdentityDetailPage'

const LOAD_WAIT_MS = 5000
const TEST_BUDGET_MS = 20000

const DEFENSE_SKILL_ID = '1010104'
const DEFENSE_SKILL_TIER = 1

function defenseCardImages(): string[] {
  const card = document.querySelector(`[data-skill-id="${DEFENSE_SKILL_ID}"]`)
  if (card === null) return []
  return [...card.querySelectorAll('img')].map((image) => image.getAttribute('src') ?? '')
}

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('identity 10101 defense skill 1010104 per uptie', () => {
  it(
    'shows AZURE with the uptie-4 icon at uptie 4, and NEUTRAL without it at uptie 1',
    async () => {
      const azureFrame = getSkillFramePath('AZURE', DEFENSE_SKILL_TIER)
      const neutralFrame = getSkillFramePath('NEUTRAL', DEFENSE_SKILL_TIER)
      const uptie4Icon = getSkillImagePathFromIconID('1010104_4')
      const baseIcon = getSkillImagePath('10101', DEFENSE_SKILL_ID)
      expect(new Set([azureFrame, neutralFrame]).size).toBe(2)
      expect(new Set([uptie4Icon, baseIcon]).size).toBe(2)

      mountRoute('/identity/$id', '/identity/10101', IdentityDetailPage)

      await waitFor(() => expect(defenseCardImages()).toContain(uptie4Icon), {
        timeout: LOAD_WAIT_MS,
      })
      expect(defenseCardImages()).toContain(azureFrame)
      expect(defenseCardImages()).not.toContain(neutralFrame)

      fireEvent.click(screen.getByRole('button', { name: /tier 1/i }))

      await waitFor(() => expect(defenseCardImages()).toContain(neutralFrame))
      expect(defenseCardImages()).not.toContain(azureFrame)
      expect(defenseCardImages()).not.toContain(uptie4Icon)
      expect(defenseCardImages()).toContain(baseIcon)
    },
    TEST_BUDGET_MS,
  )
})
