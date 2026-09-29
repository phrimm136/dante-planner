import { describe, it, expect, beforeEach } from 'vitest'
import { waitFor } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { mountRoute } from '@/test-utils/mountRoute'
import IdentityDetailPage from '../IdentityDetailPage'

const LOAD_WAIT_MS = 10000
const TEST_BUDGET_MS = 30000

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

function requireSlot(name: string): HTMLElement {
  const element = document.querySelector<HTMLElement>(`[data-slot="${name}"]`)
  if (element === null) throw new Error(`no ${name} slot rendered`)
  return element
}

describe('IdentityDetailPage separators', () => {
  it(
    'separates only the skill 1, skill 2, skill 3 and defense groups of 10310 and puts no rule under a section title',
    async () => {
      mountRoute('/identity/$id', '/identity/10310', IdentityDetailPage)
      await waitFor(
        () =>
          expect(requireSlot('detail-sections').querySelector('[data-skill-id]')).not.toBeNull(),
        { timeout: LOAD_WAIT_MS },
      )

      const sections = requireSlot('detail-sections')
      expect(sections.querySelectorAll('[data-slot="skill-group-separator"]')).toHaveLength(3)
      expect(sections.querySelector('.divide-y')).toBeNull()
      for (const title of sections.querySelectorAll('h2')) {
        expect(title.nextElementSibling?.getAttribute('data-slot')).not.toBe('separator')
      }
    },
    TEST_BUDGET_MS,
  )
})
