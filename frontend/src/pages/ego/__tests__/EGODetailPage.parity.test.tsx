import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Suspense } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'

import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import { createTestQueryClient } from '@/test-utils/queryClient'
import EGODetailPage from '../EGODetailPage'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  const { egoTranslations } = await import('./egoDetailFixtures')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, fallback?: string) => egoTranslations[key] ?? fallback ?? key,
      i18n: { language: 'EN' },
    }),
  }
})

vi.mock('@tanstack/react-router', () => ({
  useParams: () => ({ id: '20102' }),
}))

vi.mock('@/pages/ego/hooks/useEGODetailData', async () => {
  const { egoSpec20102, egoI18n20102 } = await import('./egoDetailFixtures')
  return {
    useEGODetailSpec: () => egoSpec20102,
    useEGODetailI18n: () => egoI18n20102,
    useEGODetailData: () => ({ spec: egoSpec20102, i18n: egoI18n20102 }),
  }
})

vi.mock('@/components/hooks/use-is-breakpoint', () => ({
  useIsBreakpoint: vi.fn(() => false),
}))

// Suspends on shared filter i18n; stubbed so the snapshots never race its resolution
vi.mock('@/components/layout/EntityMetaInfo', () => ({
  EntityMetaInfo: ({ season, updateDate }: { season: number; updateDate: number }) => (
    <div data-testid="entity-meta">
      {season}/{updateDate}
    </div>
  ),
}))

const SKILL_TABS = [/awakening/i, /corrosion/i] as const
const SKILL_TAB_NAMES = ['awaken', 'erosion'] as const

/** Radix stamps a transient presence-animation style that has no bearing on structure. */
function normalize(html: string): string {
  return html.replaceAll(' style="animation-duration: 0s;"', '')
}

/** Every granular i18n boundary has resolved, so no skeleton is captured mid-flight. */
async function waitForI18nSettled(container: HTMLElement) {
  await waitFor(() => {
    if (container.querySelector('[data-slot="skeleton"]')) {
      throw new Error('granular i18n still suspended')
    }
  })
}

/** One matrix cell: mount, settle the reveal, drive threadspin + skill type, return the DOM. */
async function renderCell(threadspin: number, tab: RegExp, isMobile = false): Promise<string> {
  const queryClient = createTestQueryClient()
  const { container, unmount } = render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={<div data-testid="page-skeleton">Loading...</div>}>
        <EGODetailPage />
      </Suspense>
    </QueryClientProvider>,
  )

  await waitFor(() => {
    const revealed = isMobile
      ? screen.getAllByRole('tab').length === 2
      : screen.getAllByRole('button', { name: /corrosion/i }).length > 0
    if (!revealed) throw new Error('last section not revealed yet')
  })

  fireEvent.click(screen.getByRole('button', { name: new RegExp(`tier ${threadspin}`, 'i') }))
  fireEvent.click(screen.getByRole('button', { name: tab }))
  await waitForI18nSettled(container)

  const html = normalize(container.innerHTML)
  unmount()
  return html
}

beforeEach(() => {
  vi.mocked(useIsBreakpoint).mockReturnValue(false)
  vi.spyOn(global, 'fetch').mockImplementation(
    () => Promise.resolve({ ok: true, json: () => Promise.resolve({}) }) as Promise<Response>,
  )
})

describe('EGODetailPage DOM parity', () => {
  describe('desktop', () => {
    for (const threadspin of [1, 2, 4, 5]) {
      for (const [index, tab] of SKILL_TABS.entries()) {
        it(`renders threadspin ${threadspin} with ${SKILL_TAB_NAMES[index]} selected`, async () => {
          expect(await renderCell(threadspin, tab)).toMatchSnapshot()
        })
      }
    }
  })

  describe('mobile', () => {
    beforeEach(() => {
      vi.mocked(useIsBreakpoint).mockReturnValue(true)
    })

    for (const threadspin of [1, 5]) {
      for (const [index, tab] of SKILL_TABS.entries()) {
        it(`renders threadspin ${threadspin} with ${SKILL_TAB_NAMES[index]} selected`, async () => {
          expect(await renderCell(threadspin, tab, true)).toMatchSnapshot()
        })
      }
    }
  })
})
