import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Suspense } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'

import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import { prefetchDescriptionData } from '@/test-utils/prefetchDescriptionData'
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
  }
})

vi.mock('@/components/hooks/use-is-breakpoint', () => ({
  useIsBreakpoint: vi.fn(() => false),
}))

// Suspends on shared filter i18n; stubbed so the snapshots never race its resolution
vi.mock('@/components/layout/EntityMetaInfoI18n', () => ({
  EntityMetaInfoWithI18n: ({ season, updateDate }: { season: number; updateDate: number }) => (
    <div data-testid="entity-meta">
      {season}/{updateDate}
    </div>
  ),
}))

/** Radix stamps a transient presence-animation style that has no bearing on structure. */
function normalize(html: string): string {
  return html.replaceAll(' style="animation-duration: 0s;"', '').replace(/_r_[0-9a-z]+_/g, '_r_')
}

/** Every granular i18n boundary has resolved, so no skeleton is captured mid-flight. */
async function waitForI18nSettled(container: HTMLElement) {
  await waitFor(() => {
    if (container.querySelector('[data-slot="skeleton"]')) {
      throw new Error('granular i18n still suspended')
    }
  })
}

/** The lightbox button loads through lazy() with a null fallback, so no skeleton marks it pending. */
async function waitForExpandButton() {
  await screen.findByAltText('a11y.expandImage')
}

/** One matrix cell: mount, settle the reveal, drive threadspin, return the DOM. */
async function renderCell(threadspin: number): Promise<string> {
  const queryClient = createTestQueryClient()
  await prefetchDescriptionData(queryClient, 'EN')
  const { container, unmount } = render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={<div data-testid="page-skeleton">Loading...</div>}>
        <EGODetailPage />
      </Suspense>
    </QueryClientProvider>,
  )

  await waitFor(() => {
    const revealed = screen.queryAllByRole('region', { name: 'Passives' }).length > 0
    if (!revealed) throw new Error('last section not revealed yet')
  })

  fireEvent.click(screen.getByRole('button', { name: new RegExp(`tier ${threadspin}`, 'i') }))
  await waitForI18nSettled(container)
  await waitForExpandButton()

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
      it(`renders threadspin ${threadspin} with every skill`, async () => {
        expect(await renderCell(threadspin)).toMatchSnapshot()
      })
    }
  })

  describe('mobile', () => {
    beforeEach(() => {
      vi.mocked(useIsBreakpoint).mockReturnValue(true)
    })

    for (const threadspin of [1, 5]) {
      it(`renders threadspin ${threadspin} with every skill`, async () => {
        expect(await renderCell(threadspin)).toMatchSnapshot()
      })
    }
  })
})
