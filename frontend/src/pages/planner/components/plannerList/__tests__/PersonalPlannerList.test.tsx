/**
 * PersonalPlannerList.test.tsx
 *
 * Conflicts are reachable whatever the filters show, survive a partial failure
 * with the user's choices intact, and can be reopened after a dismissal.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { buildSaveablePlanner } from '@/test-utils/fixtures'
import { err } from '@/lib/result'

import type { ConflictItem, ConflictResolution } from '../../BatchConflictDialog'
import type { ConflictOutcome } from '../../../lib/conflictChoice'

const FIRST_ID = '00000000-0000-4000-8000-000000000001'
const SECOND_ID = '00000000-0000-4000-8000-000000000002'
const THIRD_ID = '00000000-0000-4000-8000-000000000003'

function conflictItem(id: string, title: string): ConflictItem {
  return {
    id,
    localPlanner: buildSaveablePlanner({ metadata: { id, title } }),
    serverPlanner: buildSaveablePlanner({ metadata: { id, title, syncVersion: 5 } }),
  }
}

const FIRST = conflictItem(FIRST_ID, 'First Run')
const SECOND = conflictItem(SECOND_ID, 'Second Run')
const THIRD = conflictItem(THIRD_ID, 'Third Run')

const plannersData = vi.hoisted(() => ({
  pendingConflicts: [] as unknown[],
  resolveConflicts: vi.fn(async (_resolutions: unknown[]): Promise<unknown[]> => []),
}))

vi.mock('../../../hooks/useMDUserPlannersData', () => ({
  useMDUserPlannersData: () => ({
    planners: [],
    totalCount: 0,
    isAuthenticated: true,
    isSyncing: false,
    pendingConflicts: plannersData.pendingConflicts,
    resolveConflicts: plannersData.resolveConflicts,
    isResolvingConflicts: false,
  }),
}))

vi.mock('@/pages/settings', () => ({
  useUserSettingsQuery: () => ({ data: { syncEnabled: true } }),
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: React.ReactNode }) => <a href="/planner">{children}</a>,
}))

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, fallback?: unknown) => (typeof fallback === 'string' ? fallback : key),
    }),
  }
})

import { PersonalPlannerList } from '../PersonalPlannerList'

/** The choice buttons of one conflict row; index 0 is the apply-to-all row. */
function choiceButton(label: string, row: number): HTMLElement {
  return screen.getAllByRole('button', { name: label })[row]!
}

describe('PersonalPlannerList', () => {
  beforeEach(() => {
    plannersData.pendingConflicts = []
    plannersData.resolveConflicts = vi.fn(async () => [])
  })

  it('shows the conflict dialog beside the empty state when the filter matches nothing', () => {
    plannersData.pendingConflicts = [FIRST, SECOND]

    render(<PersonalPlannerList page={0} search="nothing-matches-this" onPageChange={vi.fn()} />)

    // Both branches render: the filtered-empty list, and the conflicts on top of it.
    expect(screen.getByText('pages.plannerList.empty.noMatchTitle')).toBeInTheDocument()
    expect(screen.getByText('First Run')).toBeInTheDocument()
    expect(screen.getByText('Second Run')).toBeInTheDocument()
  })

  it('renders no dialog when nothing conflicts', () => {
    plannersData.pendingConflicts = []

    render(<PersonalPlannerList page={0} search="nothing-matches-this" onPageChange={vi.fn()} />)

    expect(screen.queryByText('First Run')).toBeNull()
  })

  it('keeps the choices made on the rows a partial failure left unresolved', async () => {
    const user = userEvent.setup()
    const submitted: ConflictResolution[][] = []
    plannersData.pendingConflicts = [FIRST, SECOND, THIRD]
    plannersData.resolveConflicts = vi.fn(async (resolutions: unknown[]) => {
      submitted.push(resolutions as ConflictResolution[])
      // The first resolves, the second fails, the third is never attempted.
      plannersData.pendingConflicts = [SECOND, THIRD]
      const outcomes: ConflictOutcome[] = [
        { id: FIRST_ID, result: { ok: true, value: undefined } },
        { id: SECOND_ID, result: err({ step: 'sync', error: { kind: 'quota' } }) },
      ]
      return outcomes
    })

    render(<PersonalPlannerList page={0} onPageChange={vi.fn()} />)

    await user.click(choiceButton('Use Server', 3))
    await user.click(screen.getByRole('button', { name: 'Resolve All' }))
    await user.click(screen.getByRole('button', { name: 'Resolve All' }))

    // Re-keying the dialog on the surviving ids would reset this back to the
    // destructive default between the two submissions.
    expect(submitted[1]).toEqual([
      { id: SECOND_ID, choice: 'overwrite' },
      { id: THIRD_ID, choice: 'discard' },
    ])
  })

  it('parks a dismissed batch behind a reopen affordance, choices intact', async () => {
    const user = userEvent.setup()
    const submitted: ConflictResolution[][] = []
    plannersData.pendingConflicts = [FIRST, SECOND]
    plannersData.resolveConflicts = vi.fn(async (resolutions: unknown[]) => {
      submitted.push(resolutions as ConflictResolution[])
      return []
    })

    render(<PersonalPlannerList page={0} onPageChange={vi.fn()} />)

    await user.click(choiceButton('Use Server', 2))
    await user.click(screen.getByRole('button', { name: /close/i }))

    // Dismissal parks the batch rather than discarding it.
    expect(screen.queryByText('First Run')).toBeNull()
    const reopen = screen.getByRole('button', { name: /unresolved/i })

    await user.click(reopen)

    expect(screen.getByText('First Run')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Resolve All' }))
    expect(submitted[0]).toEqual([
      { id: FIRST_ID, choice: 'overwrite' },
      { id: SECOND_ID, choice: 'discard' },
    ])
  })
})
