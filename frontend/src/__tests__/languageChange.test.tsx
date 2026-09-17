import { describe, it, expect, beforeEach } from 'vitest'
import { act, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'

import i18n from '@/lib/i18n'
import { router } from '@/lib/router'
import { mountRoute } from '@/test-utils/mountRoute'

const DATABASE_PREFIXES = ['/identity', '/ego', '/ego-gift', '/theme-pack', '/ab-event', '/keyword']

type PageModule = { default: () => ReactNode }

const PAGES: Record<string, { url: string; load: () => Promise<PageModule> }> = {
  '/identity': { url: '/identity', load: () => import('@/pages/identity/IdentityPage') },
  '/identity/$id': {
    url: '/identity/10101',
    load: () => import('@/pages/identity/IdentityDetailPage'),
  },
  '/ego': { url: '/ego', load: () => import('@/pages/ego/EGOPage') },
  '/ego/$id': { url: '/ego/20101', load: () => import('@/pages/ego/EGODetailPage') },
  '/ego-gift': { url: '/ego-gift', load: () => import('@/pages/egoGift/EGOGiftPage') },
  '/ego-gift/$id': {
    url: '/ego-gift/9001',
    load: () => import('@/pages/egoGift/EGOGiftDetailPage'),
  },
  '/theme-pack': { url: '/theme-pack', load: () => import('@/pages/themePack/ThemePackPage') },
  '/theme-pack/$id': {
    url: '/theme-pack/3001',
    load: () => import('@/pages/themePack/ThemePackDetailPage'),
  },
  '/ab-event': { url: '/ab-event', load: () => import('@/pages/abEvent/AbEventPage') },
  '/ab-event/$id': {
    url: '/ab-event/901001',
    load: () => import('@/pages/abEvent/AbEventDetailPage'),
  },
  '/keyword': { url: '/keyword', load: () => import('@/pages/keyword/KeywordPage') },
  '/keyword/$id': {
    url: '/keyword/A1c971a',
    load: () => import('@/pages/keyword/KeywordDetailPage'),
  },
}

const LOAD_WAIT_MS = 5000
const TEST_BUDGET_MS = 20000

const databaseRoutes = Object.keys(router.routesByPath).filter((path) =>
  DATABASE_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}/`)),
)

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
    expect([...databaseRoutes].sort()).toEqual(Object.keys(PAGES).sort())
  })

  it.each(databaseRoutes)(
    '%s shows the page skeleton once, never on language change',
    async (path) => {
      const page = PAGES[path]
      if (!page) throw new Error(`unregistered route ${path}`)
      mountRoute(path, page.url, (await page.load()).default)

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
