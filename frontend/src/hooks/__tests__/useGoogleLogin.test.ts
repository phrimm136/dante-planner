/**
 * useGoogleLogin.test.ts
 *
 * Verifies the shared login trigger navigates to the backend BFF start endpoint.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { startGoogleLogin } from '../useGoogleLogin'
import { env } from '@/lib/env'

const mockAssign = vi.fn()

beforeEach(() => {
  mockAssign.mockClear()
  Object.defineProperty(window, 'location', {
    writable: true,
    value: { assign: mockAssign, href: 'http://localhost:5173/planner/1' },
  })
})

describe('startGoogleLogin', () => {
  it('navigates to the backend BFF start endpoint with the current URL as returnTo', () => {
    startGoogleLogin()

    expect(mockAssign).toHaveBeenCalledTimes(1)
    const returnTo = encodeURIComponent('http://localhost:5173/planner/1')
    expect(mockAssign).toHaveBeenCalledWith(
      `${env.VITE_API_BASE_URL}/api/auth/google/start?returnTo=${returnTo}`
    )
  })

  it('encodes the full current URL (query + hash) as a single returnTo param', () => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { assign: mockAssign, href: 'http://localhost:5173/planner/1?tab=skills#sec-2' },
    })

    startGoogleLogin()

    const returnTo = encodeURIComponent('http://localhost:5173/planner/1?tab=skills#sec-2')
    expect(mockAssign).toHaveBeenCalledWith(
      `${env.VITE_API_BASE_URL}/api/auth/google/start?returnTo=${returnTo}`
    )
  })

  it('is a no-op on the server where window is undefined', () => {
    vi.stubGlobal('window', undefined)

    expect(() => startGoogleLogin()).not.toThrow()
    expect(mockAssign).not.toHaveBeenCalled()

    vi.unstubAllGlobals()
  })
})
