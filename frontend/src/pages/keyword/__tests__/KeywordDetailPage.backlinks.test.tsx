import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { mountRoute } from '@/test-utils/mountRoute'
import KeywordDetailPage from '../KeywordDetailPage'
import specFixture from './__fixtures__/battleKeywordSpecList.json'

vi.mock('@static/data/battleKeywordSpecList.json', async () => ({
  default: (await import('./__fixtures__/battleKeywordSpecList.json')).default,
}))

const LOAD_WAIT_MS = 10000
const TEST_BUDGET_MS = 30000

const BATTLE_LISTS = [
  ['keyword.relatedSinners', '/battle/sinnerEvent/', 'sinnerEvents'],
  ['keyword.relatedUnits', '/battle/enemy/', 'enemies'],
  ['keyword.relatedOthers', '/battle/assistant/', 'assistants'],
] as const

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

async function findList(labelKey: string): Promise<HTMLElement> {
  return waitFor(() => screen.getByTestId(labelKey), { timeout: LOAD_WAIT_MS })
}

describe('KeywordDetailPage battle unit backlinks', () => {
  it(
    'links Burst to the battle cards its spec entry names, on their battle routes',
    async () => {
      mountRoute('/keyword/$id', '/keyword/Burst', KeywordDetailPage)

      const counts: number[] = []
      for (const [labelKey, hrefPrefix, field] of BATTLE_LISTS) {
        const list = await findList(labelKey)
        const hrefs = [...list.querySelectorAll('a')].map((link) => link.getAttribute('href'))
        expect(hrefs).toEqual(specFixture.Burst[field].map((cardId) => `${hrefPrefix}${cardId}`))
        counts.push(hrefs.length)
      }
      expect(counts).toEqual([8, 167, 4])
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
