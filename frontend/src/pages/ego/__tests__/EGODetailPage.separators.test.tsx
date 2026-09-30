import { describe, it, expect, beforeEach } from 'vitest'
import { waitFor } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { mountRoute } from '@/test-utils/mountRoute'
import EGODetailPage from '../EGODetailPage'

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

describe('EGODetailPage separators', () => {
  it(
    'separates the two skills of 20102 and puts no rule under a section title',
    async () => {
      mountRoute('/ego/$id', '/ego/20102', EGODetailPage)
      await waitFor(
        () =>
          expect(requireSlot('detail-sections').querySelector('[data-skill-id]')).not.toBeNull(),
        { timeout: LOAD_WAIT_MS },
      )

      const sections = requireSlot('detail-sections')
      expect(sections.querySelectorAll('[data-skill-id]')).toHaveLength(2)
      expect(sections.querySelectorAll('[data-slot="skill-separator"]')).toHaveLength(1)
      expect(sections.querySelector('.divide-y')).toBeNull()
      for (const title of sections.querySelectorAll('h2')) {
        expect(title.nextElementSibling?.getAttribute('data-slot')).not.toBe('separator')
      }
    },
    TEST_BUDGET_MS,
  )

  it(
    'draws the client name plates on the skills and passives of 20102',
    async () => {
      mountRoute('/ego/$id', '/ego/20102', EGODetailPage)
      const sections = await waitFor(
        () => {
          const found = requireSlot('detail-sections')
          expect(
            found.querySelector('[data-skill-id] [data-slot="name-plate"] span'),
          ).not.toBeNull()
          expect(
            found.querySelector('[data-slot="passive-box"] [data-slot="name-plate"] span'),
          ).not.toBeNull()
          return found
        },
        { timeout: LOAD_WAIT_MS },
      )

      const skillPlates = sections.querySelectorAll<HTMLElement>(
        '[data-skill-id] [data-slot="name-plate"]',
      )
      const passivePlates = sections.querySelectorAll<HTMLElement>(
        '[data-slot="passive-box"] [data-slot="name-plate"]',
      )
      expect(skillPlates.length).toBeGreaterThan(0)
      expect(passivePlates.length).toBeGreaterThan(0)
      for (const [plates, height, fontSize] of [
        [skillPlates, '1.5912rem', '0.9844rem'],
        [passivePlates, '1.5912rem', '0.875rem'],
      ] as const) {
        for (const plate of plates) {
          const body = plate.firstElementChild as HTMLElement
          expect(body.style.height).toBe(height)
          expect((body.querySelector('span') as HTMLElement).style.fontSize).toBe(fontSize)
          expect(plate.style.marginBottom).toBe('')
        }
      }
    },
    TEST_BUDGET_MS,
  )
})
