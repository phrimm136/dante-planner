import { describe, it, expect, beforeEach, vi } from 'vitest'
import { act, render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Suspense } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'

import { createTestQueryClient } from '@/test-utils/queryClient'
import { rendersSince, resetRenderCounts, snapshotRenderCounts } from '@/test-utils/renderCounter'
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

// Suspends on shared filter i18n; stubbed so the tally never picks up its resolution
vi.mock('@/components/layout/EntityMetaInfoI18n', () => ({
  EntityMetaInfoWithI18n: ({ season, updateDate }: { season: number; updateDate: number }) => (
    <div data-testid="entity-meta">
      {season}/{updateDate}
    </div>
  ),
}))

// Left-pane leaves, tallied through the real components
vi.mock('@/shared/unitStatus/components/SinCostPanel', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('@/shared/unitStatus/components/SinCostPanel')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return { SinCostPanel: countRenders('SinCostPanel', actual.SinCostPanel) }
})

vi.mock('@/shared/unitStatus/components/SinResistancePanel', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('@/shared/unitStatus/components/SinResistancePanel')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return { SinResistancePanel: countRenders('SinResistancePanel', actual.SinResistancePanel) }
})

vi.mock('@/pages/ego/components/EGOHeaderI18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../components/EGOHeaderI18n')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return { EGOHeaderWithI18n: countRenders('EGOHeaderWithI18n', actual.EGOHeaderWithI18n) }
})

// Right-pane leaves: the skills section is the positive control, passives the bystander
vi.mock('@/pages/ego/components/EGOSkillI18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../components/EGOSkillI18n')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return { SkillsSectionI18n: countRenders('SkillsSectionI18n', actual.SkillsSectionI18n) }
})

vi.mock('@/pages/ego/components/PassiveI18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../components/PassiveI18n')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return {
    ...actual,
    PassiveCardWithSuspense: countRenders(
      'PassiveCardWithSuspense',
      actual.PassiveCardWithSuspense,
    ),
  }
})

/**
 * Waits out the progressive reveal so its per-frame renders never land in a
 * measurement.
 *
 * The idle-cache wait is load-bearing: the skill descriptions resolve their
 * keyword data after the skill cards are on screen, and `act` flushes a render
 * that is still suspended in a synchronous loop that starves the very promise
 * it is waiting on.
 */
async function renderSettledDetailPage() {
  const queryClient = createTestQueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={<div data-testid="page-skeleton">Loading...</div>}>
        <EGODetailPage />
      </Suspense>
    </QueryClientProvider>,
  )

  await waitFor(() => {
    expect(screen.getByRole('region', { name: 'Passives' })).toBeDefined()
  })
  await waitFor(() => {
    expect(queryClient.isFetching()).toBe(0)
  })
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
}

beforeEach(() => {
  resetRenderCounts()
  vi.spyOn(global, 'fetch').mockImplementation(
    () => Promise.resolve({ ok: true, json: () => Promise.resolve({}) }) as Promise<Response>,
  )
})

describe('EGODetailPage render isolation', () => {
  it('renders every 20102 skill at once, awaken before erosion', async () => {
    await renderSettledDetailPage()

    const renderedSkillIds = [...document.querySelectorAll('[data-skill-id]')].map((card) =>
      card.getAttribute('data-skill-id'),
    )
    expect(renderedSkillIds).toEqual(['2010211', '2010221'])
  })

  it('re-renders both panes when threadspin changes, since both read it', async () => {
    await renderSettledDetailPage()

    const baseline = snapshotRenderCounts()
    fireEvent.click(screen.getByRole('button', { name: /tier 1/i }))
    await waitFor(() => {
      expect(rendersSince(baseline).SkillsSectionI18n).toBeGreaterThan(0)
    })

    expect(rendersSince(baseline).PassiveCardWithSuspense).toBeGreaterThan(0)
  })
})
