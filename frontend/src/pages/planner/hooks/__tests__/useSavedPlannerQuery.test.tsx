/**
 * useSavedPlannerQuery.test.tsx
 *
 * The saved-planner query reads IndexedDB, so it must not inherit the global
 * window-focus refetch that keeps server-backed queries fresh.
 */

import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'

import type { SaveablePlanner } from '../../types/PlannerTypes'

const PLANNER_ID = '11111111-1111-4111-8111-111111111111'

const mocks = vi.hoisted(() => ({
  loadFromLocal: vi.fn(),
}))

vi.mock('../usePlannerStorage', () => ({
  usePlannerStorage: () => ({ loadFromLocal: mocks.loadFromLocal }),
}))

import { useSavedPlannerQuery } from '../useSavedPlannerQuery'
import { plannerQueryKeys } from '../../lib/plannerQueryKeys'

describe('useSavedPlannerQuery window-focus policy', () => {
  it('opts out of the global focus refetch', async () => {
    const planner = { metadata: { id: PLANNER_ID } } as unknown as SaveablePlanner
    mocks.loadFromLocal.mockResolvedValue({ ok: true, value: planner })

    // The client carries the app-wide default, so this proves an override
    // rather than the absence of a setting.
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, refetchOnWindowFocus: true } },
    })
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={queryClient}>
        <React.Suspense fallback={null}>{children}</React.Suspense>
      </QueryClientProvider>
    )

    const { result } = renderHook(() => useSavedPlannerQuery(PLANNER_ID), { wrapper })
    await waitFor(() => expect(result.current).toBe(planner))

    const detail = queryClient.getQueryCache().find({
      queryKey: plannerQueryKeys.detail(PLANNER_ID),
    })

    expect(detail?.observers[0]?.options.refetchOnWindowFocus).toBe(false)
  })
})
