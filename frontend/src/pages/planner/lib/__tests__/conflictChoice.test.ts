import { describe, it, expect, vi } from 'vitest'
import { planConflictResolution } from '../conflictChoice'

import type { ConflictResolutionContext, PlannerConflict } from '../conflictChoice'
import type { ConflictResolutionChoice } from '../../types/PlannerTypes'

const NOW = '2026-01-01T00:00:00.000Z'

function context(overrides: Partial<ConflictResolutionContext> = {}): ConflictResolutionContext {
  return {
    deviceId: 'device-1',
    now: NOW,
    newId: () => 'copy-id',
    copyTitle: (title) => `${title} [copy]`,
    ...overrides,
  }
}

const FORK_LOCAL: PlannerConflict = { forkSide: 'local', forkTitle: 'My Run' }
const FORK_INCOMING: PlannerConflict = { forkSide: 'incoming', forkTitle: 'My Run' }

describe('planConflictResolution', () => {
  const cases: {
    name: string
    choice: ConflictResolutionChoice
    conflict: PlannerConflict
    expected: string[]
  }[] = [
    {
      name: 'overwrite keeps local',
      choice: 'overwrite',
      conflict: FORK_LOCAL,
      expected: ['keepLocal'],
    },
    {
      name: 'overwrite keeps local whichever side forks',
      choice: 'overwrite',
      conflict: FORK_INCOMING,
      expected: ['keepLocal'],
    },
    {
      name: 'discard adopts incoming',
      choice: 'discard',
      conflict: FORK_LOCAL,
      expected: ['adoptIncoming'],
    },
    {
      name: 'discard adopts incoming whichever side forks',
      choice: 'discard',
      conflict: FORK_INCOMING,
      expected: ['adoptIncoming'],
    },
    {
      name: 'both forks local then leaves the original to the incoming side',
      choice: 'both',
      conflict: FORK_LOCAL,
      expected: ['forkCopy', 'adoptIncoming'],
    },
    {
      name: 'both forks incoming then leaves the original to the local side',
      choice: 'both',
      conflict: FORK_INCOMING,
      expected: ['forkCopy', 'keepLocal'],
    },
  ]

  it.each(cases)('$name', ({ choice, conflict, expected }) => {
    expect(planConflictResolution(choice, conflict, context()).map((e) => e.kind)).toEqual(expected)
  })

  it('builds the copy from the injected id, title and clock', () => {
    const [effect] = planConflictResolution('both', FORK_LOCAL, context())

    expect(effect).toEqual({
      kind: 'forkCopy',
      metadata: {
        id: 'copy-id',
        title: 'My Run [copy]',
        status: 'saved',
        syncVersion: 1,
        deviceId: 'device-1',
        createdAt: NOW,
        lastModifiedAt: NOW,
        savedAt: NOW,
      },
    })
  })

  it('titles the copy through the caller so the suffix stays translated', () => {
    const copyTitle = vi.fn((title: string) => `${title} (복사본)`)

    const [effect] = planConflictResolution('both', FORK_INCOMING, context({ copyTitle }))

    expect(copyTitle).toHaveBeenCalledWith('My Run')
    expect(effect).toMatchObject({ metadata: { title: 'My Run (복사본)' } })
  })

  it('mints an id only for the copy', () => {
    const newId = vi.fn(() => 'copy-id')

    planConflictResolution('overwrite', FORK_LOCAL, context({ newId }))
    planConflictResolution('discard', FORK_LOCAL, context({ newId }))
    expect(newId).not.toHaveBeenCalled()

    planConflictResolution('both', FORK_LOCAL, context({ newId }))
    expect(newId).toHaveBeenCalledTimes(1)
  })
})
