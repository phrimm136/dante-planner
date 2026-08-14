/**
 * PersonalPlannerList.test.tsx
 *
 * Conflicts are reachable whatever the filters show: the dialog is a sibling of
 * the list, not a child of its non-empty branch.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

import { buildSaveablePlanner } from '@/test-utils/fixtures'

import type { ConflictItem } from '../../BatchConflictDialog'

const FIRST_ID = '00000000-0000-4000-8000-000000000001'
const SECOND_ID = '00000000-0000-4000-8000-000000000002'

function conflictItem(id: string, title: string): ConflictItem {
  return {
    id,
    localPlanner: buildSaveablePlanner({ metadata: { id, title } }),
    serverPlanner: buildSaveablePlanner({ metadata: { id, title, syncVersion: 5 } }),
  }
}

const plannersData = vi.hoisted(() => ({
  pendingConflicts: [] as unknown[],
}))

vi.mock('../../../hooks/useMDUserPlannersData', () => ({
  useMDUserPlannersData: () => ({
    planners: [],
    totalCount: 0,
    isAuthenticated: true,
    isSyncing: false,
    pendingConflicts: plannersData.pendingConflicts,
    resolveConflicts: vi.fn(async () => []),
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

describe('PersonalPlannerList', () => {
  it('shows the conflict dialog beside the empty state when the filter matches nothing', () => {
    plannersData.pendingConflicts = [
      conflictItem(FIRST_ID, 'First Run'),
      conflictItem(SECOND_ID, 'Second Run'),
    ]

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
})
