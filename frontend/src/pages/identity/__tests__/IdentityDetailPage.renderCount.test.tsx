import { describe, it, expect, beforeEach, vi } from 'vitest'
import { act, render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Suspense } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'

import { createTestQueryClient } from '@/test-utils/queryClient'
import { rendersSince, resetRenderCounts, snapshotRenderCounts } from '@/test-utils/renderCounter'
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

vi.mock('@/pages/identity/hooks/useTraitsI18n', async () => {
  const { identityTraitLabels } = await import('./identityDetailFixtures')
  return { useTraitsI18n: () => identityTraitLabels }
})

vi.mock('@/pages/identity/hooks/useSanityConditionFormatter', () => ({
  useSanityConditionFormatter: () => ({
    formatAll: (conditions: string[]) => conditions.map(() => 'Formatted condition'),
  }),
}))

// Left-pane leaves, tallied through the real components
vi.mock('@/pages/identity/components/StatusPanel', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../components/StatusPanel')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return { StatusPanel: countRenders('StatusPanel', actual.StatusPanel) }
})

vi.mock('@/pages/identity/components/ResistancePanel', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../components/ResistancePanel')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return { ResistancePanel: countRenders('ResistancePanel', actual.ResistancePanel) }
})

vi.mock('@/pages/identity/components/StaggerPanel', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../components/StaggerPanel')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return { StaggerPanel: countRenders('StaggerPanel', actual.StaggerPanel) }
})

vi.mock('@/pages/identity/components/TraitsDisplay', async () => {
  const { countRenders } = await import('@/test-utils/renderCounter')
  function TraitsDisplayStub({ traits }: { traits: string[] }) {
    return <div data-testid="traits-display">Traits: {traits.length}</div>
  }
  return { TraitsDisplay: countRenders('TraitsDisplay', TraitsDisplayStub) }
})

// Right-pane leaf, the positive control
vi.mock('@/pages/identity/components/IdentitySkillI18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../components/IdentitySkillI18n')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return { SkillsSectionI18n: countRenders('SkillsSectionI18n', actual.SkillsSectionI18n) }
})

vi.mock('@/pages/identity/components/IdentityInfoPane', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../components/IdentityInfoPane')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return { IdentityInfoPane: countRenders('IdentityInfoPane', actual.IdentityInfoPane) }
})

vi.mock('@/pages/identity/components/IdentityPassivesPane', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../components/IdentityPassivesPane')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return { IdentityPassivesPane: countRenders('IdentityPassivesPane', actual.IdentityPassivesPane) }
})

vi.mock('@/pages/identity/components/IdentitySanityPane', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../components/IdentitySanityPane')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return { IdentitySanityPane: countRenders('IdentitySanityPane', actual.IdentitySanityPane) }
})

vi.mock('@/pages/identity/components/PassiveI18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../components/PassiveI18n')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return { ...actual, PassiveCardI18n: countRenders('PassiveCardI18n', actual.PassiveCardI18n) }
})

vi.mock('@/pages/identity/components/SanityI18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../components/SanityI18n')>()
  const { countRenders } = await import('@/test-utils/renderCounter')
  return {
    ...actual,
    PanicTypeSectionI18n: countRenders('PanicTypeSectionI18n', actual.PanicTypeSectionI18n),
  }
})

const LEFT_PANE_LABELS = [
  'IdentityInfoPane',
  'StatusPanel',
  'ResistancePanel',
  'StaggerPanel',
  'TraitsDisplay',
]

const UNRELATED_RIGHT_PANE_LABELS = [
  'IdentityPassivesPane',
  'IdentitySanityPane',
  'PassiveCardI18n',
  'PanicTypeSectionI18n',
]

function pick(delta: Record<string, number>, labels: string[]): Record<string, number> {
  return Object.fromEntries(
    labels.map((label) => {
      const count = delta[label]
      if (count === undefined) {
        throw new Error(`no render count recorded for ${label}`)
      }
      return [label, count]
    }),
  )
}

function renderDetailPage() {
  const queryClient = createTestQueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={<div data-testid="page-skeleton">Loading...</div>}>
        <IdentityDetailPage />
      </Suspense>
    </QueryClientProvider>,
  )
  return queryClient
}

/**
 * Waits out the progressive reveal so its per-frame renders never land in a
 * measurement. The last section on screen means the counter has reached its
 * total and stopped scheduling frames; the flush drains the final commit.
 *
 * The idle-cache wait is load-bearing: the skill and sanity text resolve their
 * keyword data after their sections are on screen, and `act` flushes a render
 * that is still suspended in a synchronous loop that starves the very promise
 * it is waiting on.
 */
async function renderSettledDetailPage() {
  const queryClient = renderDetailPage()
  await waitFor(() => {
    expect(screen.getByText(/Panic Type/i)).toBeDefined()
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

describe('IdentityDetailPage render isolation', () => {
  it('does not re-render the left pane when a skill tab is clicked', async () => {
    await renderSettledDetailPage()

    const baseline = snapshotRenderCounts()
    fireEvent.click(screen.getByRole('button', { name: /skill 2/i }))
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /skill 2/i }).style.backgroundColor).toBeTruthy()
    })

    const delta = rendersSince(baseline)
    expect(delta.SkillsSectionI18n).toBe(1)
    expect(pick(delta, LEFT_PANE_LABELS)).toEqual({
      IdentityInfoPane: 0,
      StatusPanel: 0,
      ResistancePanel: 0,
      StaggerPanel: 0,
      TraitsDisplay: 0,
    })
    expect(pick(delta, UNRELATED_RIGHT_PANE_LABELS)).toEqual({
      IdentityPassivesPane: 0,
      IdentitySanityPane: 0,
      PassiveCardI18n: 0,
      PanicTypeSectionI18n: 0,
    })
  })

  it('re-renders both panes when uptie changes, since both read it', async () => {
    await renderSettledDetailPage()

    const baseline = snapshotRenderCounts()
    fireEvent.click(screen.getByRole('button', { name: /tier 1/i }))
    await waitFor(() => {
      expect(rendersSince(baseline).SkillsSectionI18n).toBeGreaterThan(0)
    })

    const delta = rendersSince(baseline)
    expect(delta.IdentityInfoPane).toBe(1)
    expect(delta.StatusPanel).toBe(1)
    expect(delta.IdentityPassivesPane).toBe(1)
  })
})
