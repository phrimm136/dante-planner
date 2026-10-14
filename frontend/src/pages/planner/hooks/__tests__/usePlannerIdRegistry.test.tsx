import { Suspense } from 'react'
import type { ReactNode } from 'react'
import { renderHook, waitFor } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { QueryClientProvider } from '@tanstack/react-query'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { PLANNER_CONFIG } from '@/lib/constants'
import { usePlannerIdRegistry } from '../usePlannerIdRegistry'

function createWrapper() {
  const queryClient = createTestQueryClient()
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>{children}</Suspense>
      </QueryClientProvider>
    )
  }
}

async function renderRegistry() {
  const { result } = renderHook(() => usePlannerIdRegistry(), { wrapper: createWrapper() })
  await waitFor(() => expect(result.current).not.toBeNull())
  return result.current
}

const versions = PLANNER_CONFIG.mdAvailableVersions

async function poolsOf(version: number): Promise<Record<string, string[]>> {
  const pools = (await import(`@static/data/MD${version}/startEgoGiftPools.json`)) as {
    default: Record<string, string[]>
  }
  return pools.default
}

describe('usePlannerIdRegistry', () => {
  it.each([0, Math.max(...versions) + 1])(
    'has no season for content version %i outside the available versions',
    async (contentVersion) => {
      const registryFor = await renderRegistry()

      expect(registryFor(contentVersion).season).toBeUndefined()
    },
  )

  it.each([...versions])('holds the start gift pools of content version %i', async (version) => {
    const registryFor = await renderRegistry()
    const expected = await poolsOf(version)

    const pools = registryFor(version).season?.startGiftPools
    expect(
      Object.fromEntries([...(pools ?? new Map())].map(([keyword, ids]) => [keyword, [...ids]])),
    ).toEqual(expected)
  })

  it.each([
    [6, 55],
    [7, 60],
  ])('gives content version %i the identity max level %i', async (version, identityMaxLevel) => {
    const registryFor = await renderRegistry()

    expect(registryFor(version).season?.identityMaxLevel).toBe(identityMaxLevel)
  })

  it('gives every E.G.O a threadspin ceiling', async () => {
    const registry = (await renderRegistry())(PLANNER_CONFIG.mdCurrentVersion)

    expect([...registry.egoMaxThreadspin.keys()].sort()).toEqual([...registry.egoIds].sort())
  })
})
