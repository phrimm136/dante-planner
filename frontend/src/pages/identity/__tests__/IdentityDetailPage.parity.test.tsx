import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Suspense } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'

import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import { IDENTITY_LEVEL_CAP } from '@/shared/gameData'
import { prefetchDescriptionData } from '@/test-utils/prefetchDescriptionData'
import { createTestQueryClient } from '@/test-utils/queryClient'
import IdentityDetailPage from '../IdentityDetailPage'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  const { identityTranslations } = await import('./identityDetailFixtures')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, fallback?: string) => identityTranslations[key] ?? fallback ?? key,
      i18n: { language: 'EN' },
    }),
  }
})

vi.mock('@tanstack/react-router', () => ({
  useParams: () => ({ id: '10114' }),
}))

vi.mock('@/pages/identity/hooks/useIdentityDetailData', async () => {
  const { identitySpec10114, identityI18n10114 } = await import('./identityDetailFixtures')
  return {
    useIdentityDetailSpec: () => identitySpec10114,
    useIdentityDetailI18n: () => identityI18n10114,
  }
})

vi.mock('@/components/hooks/use-is-breakpoint', () => ({
  useIsBreakpoint: vi.fn(() => false),
}))

vi.mock('@/shared/sanity/hooks/usePanicInfo', () => ({
  usePanicInfo: () => ({
    data: { '9999': { name: 'Standard Panic', panicDesc: 'Standard panic effect' } },
  }),
  getPanicEntry: (_panicInfo: Record<string, unknown>, panicType: number) =>
    panicType === 9999 ? { name: 'Standard Panic', panicDesc: 'Standard panic effect' } : null,
}))

vi.mock('@/shared/filter/hooks/useUnitKeywords', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/filter/hooks/useUnitKeywords')>()
  const { identityTraitLabels } = await import('./identityDetailFixtures')
  return { ...actual, useUnitKeywords: () => identityTraitLabels }
})

vi.mock('@/shared/sanity/hooks/useSanityConditionFormatter', () => ({
  useSanityConditionFormatter: () => ({
    formatAll: (conditions: string[]) => conditions.map(() => 'Formatted condition'),
  }),
}))

// Suspends on shared filter i18n; stubbed so the snapshots never race its resolution
vi.mock('@/components/layout/EntityMetaInfoI18n', () => ({
  EntityMetaInfoWithI18n: ({ season, updateDate }: { season: number; updateDate: number }) => (
    <div data-testid="entity-meta">
      {season}/{updateDate}
    </div>
  ),
}))

/** Waits out the progressive reveal: both layouts stack every section, Sanity last. */
async function waitForFullReveal() {
  await waitFor(() => {
    const revealed = screen.getAllByText(/Panic Type/i).length > 0
    if (!revealed) throw new Error('last section not revealed yet')
  })
}

/** Every granular i18n boundary has resolved, so no skeleton is captured mid-flight. */
async function waitForI18nSettled(container: HTMLElement) {
  await waitFor(() => {
    if (container.querySelector('[data-slot="skeleton"]')) {
      throw new Error('granular i18n still suspended')
    }
  })
}

/**
 * Radix stamps a transient presence-animation style and useId-derived trigger
 * ids; neither has any bearing on structure, and the id counter shifts with
 * suspense resolution order across runs.
 */
function normalize(html: string): string {
  return html.replaceAll(' style="animation-duration: 0s;"', '').replace(/_r_[0-9a-z]+_/g, '_r_')
}

/** One matrix cell: mount, settle the reveal, drive uptie, return the DOM. */
async function renderCell(uptie: number): Promise<string> {
  const queryClient = createTestQueryClient()
  await prefetchDescriptionData(queryClient, 'EN')
  const { container, unmount } = render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={<div data-testid="page-skeleton">Loading...</div>}>
        <IdentityDetailPage />
      </Suspense>
    </QueryClientProvider>,
  )

  await waitForFullReveal()

  fireEvent.click(screen.getByRole('button', { name: new RegExp(`tier ${uptie}`, 'i') }))
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

describe('IdentityDetailPage DOM parity', () => {
  describe('desktop', () => {
    for (const uptie of [1, 2, 3, 4]) {
      it(`renders uptie ${uptie} with every skill`, async () => {
        expect(await renderCell(uptie)).toMatchSnapshot()
      })
    }
  })

  describe('mobile', () => {
    beforeEach(() => {
      vi.mocked(useIsBreakpoint).mockReturnValue(true)
    })

    for (const uptie of [1, 4]) {
      it(`renders uptie ${uptie} with every skill`, async () => {
        expect(await renderCell(uptie)).toMatchSnapshot()
      })
    }
  })

  it('renders the stats panel at a lowered level', async () => {
    const queryClient = createTestQueryClient()
    await prefetchDescriptionData(queryClient, 'EN')
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={<div data-testid="page-skeleton">Loading...</div>}>
          <IdentityDetailPage />
        </Suspense>
      </QueryClientProvider>,
    )

    await waitForFullReveal()

    const slider = screen.getByRole('slider')
    fireEvent.keyDown(slider, { key: 'ArrowLeft' })
    await waitFor(() => {
      expect(screen.getByText(`Lv. ${IDENTITY_LEVEL_CAP - 1}`)).toBeDefined()
    })
    await waitForI18nSettled(container)

    expect(normalize(container.innerHTML)).toMatchSnapshot()
  })
})
