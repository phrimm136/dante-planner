import { describe, it, expect, beforeEach } from 'vitest'
import { act, waitFor } from '@testing-library/react'

import i18n from '@/lib/i18n'
import { router } from '@/lib/router'
import { mountRoute } from '@/test-utils/mountRoute'

const DATABASE_PREFIXES = [
  '/identity',
  '/ego',
  '/ego-gift',
  '/theme-pack',
  '/ab-event',
  '/battle',
  '/keyword',
]

const PAGE_URLS: Record<string, string> = {
  '/identity': '/identity',
  '/identity/$id': '/identity/10101',
  '/ego': '/ego',
  '/ego/$id': '/ego/20101',
  '/ego-gift': '/ego-gift',
  '/ego-gift/$id': '/ego-gift/9001',
  '/theme-pack': '/theme-pack',
  '/theme-pack/$id': '/theme-pack/3001',
  '/ab-event': '/ab-event',
  '/ab-event/$id': '/ab-event/901001',
  '/battle': '/battle',
  '/battle/enemy/$id': '/battle/enemy/8063',
  '/battle/assistant/$id': '/battle/assistant/400001',
  '/battle/sinnerEvent/$id': '/battle/sinnerEvent/10101',
  '/keyword': '/keyword',
  '/keyword/$id': '/keyword/A1c971a',
}

const LOAD_WAIT_MS = 5000
const TEST_BUDGET_MS = 20000

const databaseRoutes = Object.keys(router.routesByPath).filter((path) =>
  DATABASE_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}/`)),
)

async function routeComponent(path: string) {
  const Page = router.routesByPath[path as keyof typeof router.routesByPath].options.component
  if (!Page) throw new Error(`route ${path} has no component`)
  await Page.preload?.()
  return () => <Page />
}

function pageSkeletons() {
  return document.querySelectorAll('[data-slot="page-skeleton"]').length
}

function skeletons() {
  return document.querySelectorAll('[data-slot="skeleton"]').length
}

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('database pages on language change', () => {
  it('registers every database route', () => {
    expect([...databaseRoutes].sort()).toEqual(Object.keys(PAGE_URLS).sort())
  })

  it.each(databaseRoutes)(
    '%s shows the page skeleton once, never on language change',
    async (path) => {
      const url = PAGE_URLS[path]
      if (!url) throw new Error(`unregistered route ${path}`)
      mountRoute(path, url, await routeComponent(path))

      // Under a contended worker pool the cold JSON transforms behind the card names
      // outlast the default wait, and the page import above counts toward the budget.
      await waitFor(() => expect(pageSkeletons()).toBe(0), { timeout: LOAD_WAIT_MS })
      await waitFor(() => expect(skeletons()).toBe(0), { timeout: LOAD_WAIT_MS })

      act(() => {
        void i18n.changeLanguage('KR')
      })

      expect(pageSkeletons()).toBe(0)
      await waitFor(() => expect(skeletons()).toBe(0), { timeout: LOAD_WAIT_MS })
    },
    TEST_BUDGET_MS,
  )
})
