import { beforeEach, describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { mountRoute } from '@/test-utils/mountRoute'
import KeywordDetailPage from '../KeywordDetailPage'

const LOAD_WAIT_MS = 10000
const TEST_BUDGET_MS = 30000

const BATTLE_LISTS = [
  ['keyword.relatedSinners', '/battle/sinnerEvent/'],
  ['keyword.relatedUnits', '/battle/enemy/'],
  ['keyword.relatedOthers', '/battle/assistant/'],
] as const

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

async function findList(labelKey: string): Promise<HTMLElement> {
  return waitFor(() => screen.getByTestId(labelKey), { timeout: LOAD_WAIT_MS })
}

describe('KeywordDetailPage battle unit backlinks', () => {
  it(
    'links Burst to 8 sinners, 212 units and 4 others on their battle routes',
    async () => {
      mountRoute('/keyword/$id', '/keyword/Burst', KeywordDetailPage)

      const counts: number[] = []
      for (const [labelKey, hrefPrefix] of BATTLE_LISTS) {
        const list = await findList(labelKey)
        const links = [...list.querySelectorAll('a')]
        expect(links.length).toBeGreaterThan(0)
        for (const link of links) {
          expect(link.getAttribute('href')).toMatch(new RegExp(`^${hrefPrefix}\\d+$`))
        }
        counts.push(links.length)
      }
      expect(counts).toEqual([8, 212, 4])
    },
    TEST_BUDGET_MS,
  )

  it(
    'shows "-" in all three lists for a keyword no battle unit carries',
    async () => {
      mountRoute('/keyword/$id', '/keyword/A1c971a', KeywordDetailPage)

      for (const [labelKey] of BATTLE_LISTS) {
        const list = await findList(labelKey)
        expect(list.querySelectorAll('a')).toHaveLength(0)
        expect(list.lastElementChild?.textContent).toBe('-')
      }
    },
    TEST_BUDGET_MS,
  )
})
