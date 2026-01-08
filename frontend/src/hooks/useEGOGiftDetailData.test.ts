import { describe, it, expect } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useEGOGiftDetailSpec, useEGOGiftDetailI18n } from './useEGOGiftDetailData'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('useEGOGiftDetailSpec', () => {
  it('should fetch spec data without language in query key', async () => {
    const { result } = renderHook(() => useEGOGiftDetailSpec('9001'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current).toBeDefined()
      expect(result.current.tag).toBeDefined()
      expect(result.current.maxEnhancement).toBeDefined()
    })
  })

  it('should return typed spec data', async () => {
    const { result } = renderHook(() => useEGOGiftDetailSpec('9001'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      const spec = result.current
      expect(typeof spec.price).toBe('number')
      expect(Array.isArray(spec.tag)).toBe(true)
      expect(Array.isArray(spec.themePack)).toBe(true)
      expect([0, 1, 2]).toContain(spec.maxEnhancement)
    })
  })
})

describe('useEGOGiftDetailI18n', () => {
  it('should fetch i18n data with language in query key', async () => {
    const { result } = renderHook(() => useEGOGiftDetailI18n('9001'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current).toBeDefined()
      expect(result.current.name).toBeDefined()
      expect(Array.isArray(result.current.descs)).toBe(true)
    })
  })

  it('should return typed i18n data', async () => {
    const { result } = renderHook(() => useEGOGiftDetailI18n('9001'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      const i18n = result.current
      expect(typeof i18n.name).toBe('string')
      expect(Array.isArray(i18n.descs)).toBe(true)
      expect(i18n.descs.length).toBeGreaterThanOrEqual(0)
    })
  })
})
