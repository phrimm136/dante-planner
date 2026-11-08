import { describe, it, expect, beforeEach } from 'vitest'
import { fireEvent, waitFor, within } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { mountRoute } from '@/test-utils/mountRoute'
import AbEventPage from '../AbEventPage'
import { abEventFilterStore } from '../stores/abEventFilterStore'

const LOAD_WAIT_MS = 10000
const TEST_BUDGET_MS = 30000

beforeEach(async () => {
  await i18n.changeLanguage('EN')
  abEventFilterStore.resetAll()
})

function databaseText(key: string): string {
  return i18n.t(key, { ns: 'database' })
}

function desktop() {
  const aside = document.querySelector('aside')
  if (aside === null) throw new Error('no desktop filter sidebar')
  return within(aside)
}

function sectionTitles(container: HTMLElement): string[] {
  return Array.from(container.querySelectorAll('span.uppercase')).map(
    (span) => span.firstChild?.textContent ?? '',
  )
}

describe('AbEventPage', () => {
  it(
    'wires the result filter under filters.eventResult before the EGO gift section',
    async () => {
      mountRoute('/abEvent', '/abEvent', AbEventPage)
      await waitFor(
        () =>
          expect(
            desktop().getByPlaceholderText(databaseText('pages.abEvent.searchBar')),
          ).toBeVisible(),
        { timeout: LOAD_WAIT_MS },
      )

      const aside = document.querySelector('aside')
      if (aside === null) throw new Error('no desktop filter sidebar')
      const titles = sectionTitles(aside)
      const giftIndex = titles.indexOf(databaseText('filters.egoGift'))
      expect(giftIndex).toBeGreaterThanOrEqual(0)
      expect(titles[giftIndex - 1]).toBe(databaseText('filters.eventResult'))

      fireEvent.click(
        desktop().getByRole('button', {
          name: databaseText('filters.eventResultTypes.levelUp'),
        }),
      )
      expect(abEventFilterStore.store.getState().values.selectedResultTypes).toEqual(
        new Set(['levelUp']),
      )
      expect(
        desktop().getByRole('button', {
          name: databaseText('filters.eventResultTypes.levelUp'),
        }),
      ).toHaveAttribute('aria-pressed', 'true')

      abEventFilterStore.resetAll()
      expect(abEventFilterStore.store.getState().values.selectedResultTypes.size).toBe(0)
    },
    TEST_BUDGET_MS,
  )
})
