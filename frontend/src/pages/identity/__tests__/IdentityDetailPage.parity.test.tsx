import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Suspense } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'

import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import { MAX_LEVEL } from '@/shared/gameData'
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
    useIdentityDetailData: () => ({ spec: identitySpec10114, i18n: identityI18n10114 }),
  }
})

vi.mock('@/components/hooks/use-is-breakpoint', () => ({
  useIsBreakpoint: vi.fn(() => false),
}))

vi.mock('@/pages/identity/hooks/usePanicInfo', () => ({
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

vi.mock('@/pages/identity/hooks/useSanityConditionFormatter', () => ({
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

const SKILL_TABS = [/skill 1/i, /skill 2/i, /skill 3/i, /defense/i] as const
const SKILL_TAB_NAMES = ['skill1', 'skill2', 'skill3', 'skillDef'] as const

/**
 * Waits out the progressive reveal. Desktop stacks every section, mobile
 * collapses them into tabs, so each layout gets its own last-section signal.
 */
async function waitForFullReveal(isMobile: boolean) {
  await waitFor(() => {
    const revealed = isMobile
      ? screen.getAllByRole('tab').length === 3
      : screen.getAllByText(/Panic Type/i).length > 0
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

/** Radix stamps a transient presence-animation style that has no bearing on structure. */
function normalize(html: string): string {
  return html.replaceAll(' style="animation-duration: 0s;"', '')
}

/** One matrix cell: mount, settle the reveal, drive uptie + skill slot, return the DOM. */
async function renderCell(uptie: number, tab: RegExp, isMobile = false): Promise<string> {
  const queryClient = createTestQueryClient()
  const { container, unmount } = render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={<div data-testid="page-skeleton">Loading...</div>}>
        <IdentityDetailPage />
      </Suspense>
    </QueryClientProvider>,
  )

  await waitForFullReveal(isMobile)

  fireEvent.click(screen.getByRole('button', { name: new RegExp(`tier ${uptie}`, 'i') }))
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

describe('IdentityDetailPage DOM parity', () => {
  describe('desktop', () => {
    for (const uptie of [1, 2, 3, 4]) {
      for (const [index, tab] of SKILL_TABS.entries()) {
        it(`renders uptie ${uptie} with ${SKILL_TAB_NAMES[index]} selected`, async () => {
          expect(await renderCell(uptie, tab)).toMatchSnapshot()
        })
      }
    }
  })

  describe('mobile', () => {
    beforeEach(() => {
      vi.mocked(useIsBreakpoint).mockReturnValue(true)
    })

    for (const uptie of [1, 4]) {
      for (const index of [0, 2]) {
        it(`renders uptie ${uptie} with ${SKILL_TAB_NAMES[index]} selected`, async () => {
          expect(await renderCell(uptie, SKILL_TABS[index], true)).toMatchSnapshot()
        })
      }
    }
  })

  it('renders the stats panel at a lowered level', async () => {
    const queryClient = createTestQueryClient()
    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={<div data-testid="page-skeleton">Loading...</div>}>
          <IdentityDetailPage />
        </Suspense>
      </QueryClientProvider>,
    )

    await waitForFullReveal(false)

    const slider = screen.getByRole('slider')
    fireEvent.keyDown(slider, { key: 'ArrowLeft' })
    await waitFor(() => {
      expect(screen.getByText(`Lv. ${MAX_LEVEL - 1}`)).toBeDefined()
    })
    await waitForI18nSettled(container)

    expect(normalize(container.innerHTML)).toMatchSnapshot()
  })
})
