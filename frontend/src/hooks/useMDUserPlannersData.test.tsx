/**
 * useMDUserPlannersData.test.tsx
 *
 * Tests for user (personal) planners data hook.
 * Uses Vitest for testing query key structure.
 */

import { describe, it, expect } from 'vitest'
import { userPlannersQueryKeys } from './useMDUserPlannersData'

describe('userPlannersQueryKeys', () => {
  it('creates consistent base key', () => {
    const key = userPlannersQueryKeys.all
    expect(key).toEqual(['userPlanners'])
  })

  it('creates unique keys for different auth states', () => {
    const guestKey = userPlannersQueryKeys.list(false)
    const authKey = userPlannersQueryKeys.list(true)

    expect(guestKey).not.toEqual(authKey)
    expect(guestKey[2]).toEqual({ isAuthenticated: false })
    expect(authKey[2]).toEqual({ isAuthenticated: true })
  })

  it('includes auth state in list key for cache separation', () => {
    const key = userPlannersQueryKeys.list(true)

    expect(key[0]).toBe('userPlanners')
    expect(key[1]).toBe('list')
    expect(key[2]).toHaveProperty('isAuthenticated', true)
  })
})
