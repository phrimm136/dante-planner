import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Suspense } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'

import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import { diffHtml, normalizeHtml } from '@/test-utils/domDiff'
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

const SKILL_TABS = [/skill 1/i, /skill 2/i, /skill 3/i, /defense/i] as const
const SKILL_TAB_NAMES = ['skill1', 'skill2', 'skill3', 'skillDef'] as const

/** Imported lazily under a null Suspense fallback, so the skeleton check cannot see it pending. */
const EXPAND_IMAGE_BUTTON = 'a11y.expandImage'

/**
 * Waits out the progressive reveal. Desktop stacks every section, mobile
 * collapses them into tabs, so each layout gets its own last-section signal.
 */
async function waitForFullReveal(isMobile: boolean) {
  await waitFor(() => {
    const revealed =
      (isMobile
        ? screen.getAllByRole('tab').length === 3
        : screen.getAllByText(/Panic Type/i).length > 0) &&
      screen.queryAllByRole('button', { name: EXPAND_IMAGE_BUTTON }).length > 0
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
  return html
    .replaceAll(' style="animation-duration: 0s;"', '')
    .replace(/radix-_r_[0-9a-z]+_/g, 'radix-_r_')
}

/** One matrix cell: mount, settle the reveal, drive uptie + skill slot, return the DOM. */
async function renderCell(uptie: number, tab: RegExp, isMobile = false): Promise<string> {
  vi.mocked(useIsBreakpoint).mockReturnValue(isMobile)
  const queryClient = createTestQueryClient()
  await prefetchDescriptionData(queryClient, 'EN')
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

const baselines = new Map<boolean, Promise<string>>()

/** Each layout's baseline cell (uptie 1, skill 1), rendered once per file run. */
function baselineHtml(isMobile: boolean): Promise<string> {
  let baseline = baselines.get(isMobile)
  if (!baseline) {
    baseline = renderCell(1, SKILL_TABS[0], isMobile)
    baselines.set(isMobile, baseline)
  }
  return baseline
}

/** The baseline cell's whole DOM; for every other cell, its diff against that baseline. */
async function cellSnapshot(uptie: number, tab: RegExp, isMobile: boolean): Promise<string> {
  const baseline = await baselineHtml(isMobile)
  return uptie === 1 && tab === SKILL_TABS[0]
    ? normalizeHtml(baseline)
    : diffHtml(baseline, await renderCell(uptie, tab, isMobile))
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
          expect(await cellSnapshot(uptie, tab, false)).toMatchSnapshot()
        })
      }
    }
  })

  describe('mobile', () => {
    for (const uptie of [1, 4]) {
      for (const [index, tab] of SKILL_TABS.entries()) {
        if (index !== 0 && index !== 2) continue
        it(`renders uptie ${uptie} with ${SKILL_TAB_NAMES[index]} selected`, async () => {
          expect(await cellSnapshot(uptie, tab, true)).toMatchSnapshot()
        })
      }
    }
  })

  it('renders the stats panel at a lowered level', async () => {
    const baseline = await baselineHtml(false)
    vi.mocked(useIsBreakpoint).mockReturnValue(false)
    const queryClient = createTestQueryClient()
    await prefetchDescriptionData(queryClient, 'EN')
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
      expect(screen.getByText('Lv. 64')).toBeDefined()
    })
    await waitForI18nSettled(container)

    expect(diffHtml(baseline, normalize(container.innerHTML))).toMatchSnapshot()
  })
})
