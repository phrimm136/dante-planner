import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { QueryObserverOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { Suspense, type ReactNode } from 'react'

import { STATIC_DATA_STALE_TIME } from '@/lib/constants'
import {
  useEntityListData,
  useEntityListSpec,
  useEntityListI18n,
  useEntityListI18nDeferred,
  type EntityListDataConfig,
} from '../useEntityListData'

const language = { current: 'EN' }

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ i18n: { language: language.current } }),
}))

const SpecSchema = z.record(z.string(), z.object({ rank: z.number() }))
const NameSchema = z.record(z.string(), z.string())

function createConfig(
  overrides: Partial<EntityListDataConfig<z.infer<typeof SpecSchema>, Record<string, string>>> = {},
): EntityListDataConfig<z.infer<typeof SpecSchema>, Record<string, string>> {
  return {
    kind: 'identity',
    specImport: () => Promise.resolve({ default: { '10101': { rank: 3 } } }),
    specSchema: SpecSchema,
    i18nImport: (lang) => Promise.resolve({ default: { '10101': `Yi Sang ${lang}` } }),
    i18nSchema: NameSchema,
    emptyI18n: {},
    ...overrides,
  }
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>{children}</Suspense>
    </QueryClientProvider>
  )
  return { queryClient, wrapper }
}

describe('useEntityListData', () => {
  it('caches spec and i18n under the slice tuples the pages already use', async () => {
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useEntityListData(createConfig()), { wrapper })

    await waitFor(() => {
      expect(result.current.spec).toEqual({ '10101': { rank: 3 } })
    })
    expect(result.current.i18n).toEqual({ '10101': 'Yi Sang EN' })

    const keys = queryClient
      .getQueryCache()
      .getAll()
      .map((query) => query.queryKey)
    expect(keys).toContainEqual(['identity', 'list', 'spec'])
    expect(keys).toContainEqual(['identity', 'list', 'i18n', 'EN'])
  })

  it('derives the query key namespace from the config kind', async () => {
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useEntityListSpec(createConfig({ kind: 'egoGift' })), {
      wrapper,
    })

    await waitFor(() => {
      expect(result.current).toBeDefined()
    })
    const keys = queryClient
      .getQueryCache()
      .getAll()
      .map((query) => query.queryKey)
    expect(keys).toContainEqual(['egoGift', 'list', 'spec'])
  })

  it('labels validation failures with "<kind> specList"', async () => {
    const { queryClient, wrapper } = createWrapper()
    const config = createConfig({ specImport: () => Promise.resolve({ default: { bad: 1 } }) })

    renderHook(() => useEntityListSpec(config), { wrapper })

    await waitFor(() => {
      const query = queryClient.getQueryCache().find({ queryKey: ['identity', 'list', 'spec'] })
      expect(query?.state.error).toBeInstanceOf(Error)
    })
    const query = queryClient.getQueryCache().find({ queryKey: ['identity', 'list', 'spec'] })
    expect(query?.state.error?.message).toMatch(/^\[identity specList\] Validation failed: /)
  })

  it('holds both queries at the static staleTime', async () => {
    const { queryClient, wrapper } = createWrapper()

    renderHook(() => useEntityListI18n(createConfig()), { wrapper })

    await waitFor(() => {
      const query = queryClient
        .getQueryCache()
        .find({ queryKey: ['identity', 'list', 'i18n', 'EN'] })
      expect(query?.state.data).toBeDefined()
    })
    const query = queryClient.getQueryCache().find({ queryKey: ['identity', 'list', 'i18n', 'EN'] })
    // The cache stores the observer's defaulted options, which is where staleTime lives.
    const options = query?.options as QueryObserverOptions | undefined
    expect(options?.staleTime).toBe(STATIC_DATA_STALE_TIME)
  })

  it('returns emptyI18n from the deferred hook until the name list resolves', async () => {
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useEntityListI18nDeferred(createConfig()), { wrapper })

    expect(result.current).toEqual({})

    await waitFor(() => {
      expect(result.current).toEqual({ '10101': 'Yi Sang EN' })
    })
  })

  it('keeps the previous language visible while the next name list loads', async () => {
    const { wrapper } = createWrapper()

    language.current = 'EN'
    const { result, rerender } = renderHook(() => useEntityListI18nDeferred(createConfig()), {
      wrapper,
    })

    await waitFor(() => {
      expect(result.current).toEqual({ '10101': 'Yi Sang EN' })
    })

    language.current = 'KR'
    rerender()

    // keepPreviousData: no flash back to emptyI18n on the language switch
    expect(result.current).toEqual({ '10101': 'Yi Sang EN' })

    await waitFor(() => {
      expect(result.current).toEqual({ '10101': 'Yi Sang KR' })
    })
    language.current = 'EN'
  })
})
