import { describe, it, expect, vi, beforeEach } from 'vitest'
import { withRollback } from '../withRollback'

beforeEach(() => {
  vi.restoreAllMocks()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('withRollback', () => {
  it('runs create then rest and never rolls back on success', async () => {
    const order: string[] = []

    await withRollback({
      create: async () => {
        order.push('create')
      },
      rest: async () => {
        order.push('rest')
      },
      rollback: async () => {
        order.push('rollback')
      },
    })

    expect(order).toEqual(['create', 'rest'])
  })

  it('rolls back and rethrows when a later step fails', async () => {
    const rollback = vi.fn().mockResolvedValue(undefined)
    const failure = new Error('rest failed')

    await expect(
      withRollback({
        create: async () => {},
        rest: () => Promise.reject(failure),
        rollback,
      }),
    ).rejects.toBe(failure)

    expect(rollback).toHaveBeenCalledTimes(1)
  })

  it('does not roll back when create itself fails', async () => {
    const rollback = vi.fn().mockResolvedValue(undefined)
    const rest = vi.fn().mockResolvedValue(undefined)
    const failure = new Error('create failed')

    await expect(
      withRollback({ create: () => Promise.reject(failure), rest, rollback }),
    ).rejects.toBe(failure)

    expect(rollback).not.toHaveBeenCalled()
    expect(rest).not.toHaveBeenCalled()
  })

  it('surfaces the original error even when the rollback also fails', async () => {
    const failure = new Error('rest failed')

    await expect(
      withRollback({
        create: async () => {},
        rest: () => Promise.reject(failure),
        rollback: () => Promise.reject(new Error('rollback failed')),
      }),
    ).rejects.toBe(failure)
  })
})
