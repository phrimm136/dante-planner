/**
 * BatchConflictDialog.test.tsx
 *
 * The dialog can be closed, a new batch reopens it, and a failed item reports
 * against its own row rather than as one message for the whole batch.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { buildSaveablePlanner } from '@/test-utils/fixtures'
import { err } from '@/lib/result'

import type { ConflictOutcome } from '../../lib/conflictChoice'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, fallback?: unknown) => (typeof fallback === 'string' ? fallback : key),
    }),
  }
})

import { BatchConflictDialog } from '../BatchConflictDialog'
import type { ConflictItem } from '../BatchConflictDialog'

const FIRST_ID = '00000000-0000-4000-8000-000000000001'
const SECOND_ID = '00000000-0000-4000-8000-000000000002'

function conflictItem(id: string, title: string): ConflictItem {
  return {
    id,
    localPlanner: buildSaveablePlanner({ metadata: { id, title } }),
    serverPlanner: buildSaveablePlanner({ metadata: { id, title, syncVersion: 5 } }),
  }
}

const CONFLICTS = [conflictItem(FIRST_ID, 'First Run'), conflictItem(SECOND_ID, 'Second Run')]

describe('BatchConflictDialog', () => {
  it('closes on the close button, leaving the list reachable', async () => {
    const user = userEvent.setup()
    render(<BatchConflictDialog open conflicts={CONFLICTS} onResolve={vi.fn()} />)

    expect(screen.getByText('First Run')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /close/i }))

    expect(screen.queryByText('First Run')).toBeNull()
  })

  it('reopens for a new batch, which the consumer mounts under its own key', async () => {
    const user = userEvent.setup()
    const { rerender } = render(
      <BatchConflictDialog key={FIRST_ID} open conflicts={[CONFLICTS[0]!]} onResolve={vi.fn()} />,
    )
    await user.click(screen.getByRole('button', { name: /close/i }))
    expect(screen.queryByText('First Run')).toBeNull()

    rerender(
      <BatchConflictDialog key={SECOND_ID} open conflicts={[CONFLICTS[1]!]} onResolve={vi.fn()} />,
    )

    expect(screen.getByText('Second Run')).toBeInTheDocument()
  })

  it('reports a failed item against its own row, leaving the others unmarked', () => {
    const outcomes: ConflictOutcome[] = [
      { id: FIRST_ID, result: { ok: true, value: undefined } },
      { id: SECOND_ID, result: err({ step: 'sync', error: { kind: 'quota' } }) },
    ]

    render(
      <BatchConflictDialog open conflicts={CONFLICTS} onResolve={vi.fn()} outcomes={outcomes} />,
    )

    expect(screen.queryByTestId(`outcome-${FIRST_ID}`)).toBeNull()
    expect(screen.getByTestId(`outcome-${SECOND_ID}`)).toHaveTextContent(
      'planner:pages.plannerMD.save.quotaExceeded',
    )
  })

  it('submits one resolution per conflict, defaulting to keeping the local side', async () => {
    const user = userEvent.setup()
    const onResolve = vi.fn()
    render(<BatchConflictDialog open conflicts={CONFLICTS} onResolve={onResolve} />)

    await user.click(screen.getByRole('button', { name: 'Resolve All' }))

    expect(onResolve).toHaveBeenCalledWith([
      { id: FIRST_ID, choice: 'overwrite' },
      { id: SECOND_ID, choice: 'overwrite' },
    ])
  })
})
