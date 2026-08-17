import { describe, it, expect, vi } from 'vitest'
import { ok, err } from '../result'
import { withRollback } from '../withRollback'

describe('withRollback', () => {
  it('runs create then rest and never rolls back on success', async () => {
    const order: string[] = []

    const outcome = await withRollback<string>({
      create: async () => {
        order.push('create')
        return ok(undefined)
      },
      rest: async () => {
        order.push('rest')
        return ok(undefined)
      },
      rollback: async () => {
        order.push('rollback')
        return ok(undefined)
      },
    })

    expect(outcome).toEqual({ kind: 'completed' })
    expect(order).toEqual(['create', 'rest'])
  })

  it('rolls back and reports the failure when a later step fails', async () => {
    const rollback = vi.fn().mockResolvedValue(ok(undefined))

    const outcome = await withRollback<string>({
      create: async () => ok(undefined),
      rest: async () => err('rest failed'),
      rollback,
    })

    expect(outcome).toEqual({ kind: 'undone', error: 'rest failed' })
    expect(rollback).toHaveBeenCalledTimes(1)
  })

  it('does not roll back when create itself fails', async () => {
    const rollback = vi.fn().mockResolvedValue(ok(undefined))
    const rest = vi.fn().mockResolvedValue(ok(undefined))

    const outcome = await withRollback<string>({
      create: async () => err('create failed'),
      rest,
      rollback,
    })

    expect(outcome).toEqual({ kind: 'undone', error: 'create failed' })
    expect(rollback).not.toHaveBeenCalled()
    expect(rest).not.toHaveBeenCalled()
  })

  it('reports both causes when the rollback also fails', async () => {
    const outcome = await withRollback<string>({
      create: async () => ok(undefined),
      rest: async () => err('rest failed'),
      rollback: async () => err('rollback failed'),
    })

    expect(outcome).toEqual({
      kind: 'undoFailed',
      error: 'rest failed',
      rollbackError: 'rollback failed',
    })
  })
})
