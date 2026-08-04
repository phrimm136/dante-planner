import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { z } from 'zod'
import { Suspense, type ReactNode } from 'react'

import { STATIC_DATA_STALE_TIME } from '@/lib/constants'
import {
  useEntityDetailData,
  useEntityDetailSpec,
  type EntityDetailDataConfig,
} from '../useEntityDetailData'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ i18n: { language: 'EN' } }),
}))

const SpecSchema = z.object({ rank: z.number() })
const I18nSchema = z.object({ name: z.string() })

function createConfig(
  overrides: Partial<
    EntityDetailDataConfig<z.infer<typeof SpecSchema>, z.infer<typeof I18nSchema>>
  > = {},
): EntityDetailDataConfig<z.infer<typeof SpecSchema>, z.infer<typeof I18nSchema>> {
  return {
    kind: 'identity',
    specImport: () => Promise.resolve({ default: { rank: 3 } }),
    specSchema: SpecSchema,
    i18nImport: (id, lang) => Promise.resolve({ default: { name: `${id}-${lang}` } }),
    i18nSchema: I18nSchema,
    ...overrides,
  }
}

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>{children}</Suspense>
    </QueryClientProvider>
  )
  return { queryClient, wrapper }
}

describe('useEntityDetailData', () => {
  it('caches under the language-free detail tuple and the per-language i18n tuple', async () => {
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useEntityDetailData(createConfig(), '10101'), { wrapper })

    await waitFor(() => {
      expect(result.current.spec).toEqual({ rank: 3 })
    })
    expect(result.current.i18n).toEqual({ name: '10101-EN' })

    const keys = queryClient
      .getQueryCache()
      .getAll()
      .map((query) => query.queryKey)
    expect(keys).toContainEqual(['identity', '10101'])
    expect(keys).toContainEqual(['identity', '10101', 'i18n', 'EN'])
  })

  it('derives the namespace from the config kind', async () => {
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(
      () => useEntityDetailSpec(createConfig({ kind: 'ego' }), '20101'),
      { wrapper },
    )

    await waitFor(() => {
      expect(result.current).toEqual({ rank: 3 })
    })
    const keys = queryClient
      .getQueryCache()
      .getAll()
      .map((query) => query.queryKey)
    expect(keys).toContainEqual(['ego', '20101'])
  })

  it('labels validation failures with "<kind> / <id>"', async () => {
    const { queryClient, wrapper } = createWrapper()
    const config = createConfig({ specImport: () => Promise.resolve({ default: {} }) })

    renderHook(() => useEntityDetailSpec(config, '10101'), { wrapper })

    await waitFor(() => {
      const query = queryClient.getQueryCache().find({ queryKey: ['identity', '10101'] })
      expect(query?.state.error).toBeInstanceOf(Error)
    })
    const query = queryClient.getQueryCache().find({ queryKey: ['identity', '10101'] })
    expect(query?.state.error?.message).toMatch(/^\[identity \/ 10101\] Validation failed: /)
  })

  it('uses the static staleTime and does not keep previous data', async () => {
    const { queryClient, wrapper } = createWrapper()

    renderHook(() => useEntityDetailData(createConfig(), '10101'), { wrapper })

    await waitFor(() => {
      const query = queryClient.getQueryCache().find({ queryKey: ['identity', '10101'] })
      expect(query?.state.data).toBeDefined()
    })
    const spec = queryClient.getQueryCache().find({ queryKey: ['identity', '10101'] })
    const i18n = queryClient.getQueryCache().find({ queryKey: ['identity', '10101', 'i18n', 'EN'] })
    expect(spec?.options.staleTime).toBe(STATIC_DATA_STALE_TIME)
    expect(i18n?.options.placeholderData).toBeUndefined()
  })
})
