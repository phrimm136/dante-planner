import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useEGOGiftDetailSpec, useEGOGiftDetailI18n } from '../useEGOGiftDetailData'

// Mock react-i18next to provide a language
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'EN' },
    }),
  }
})

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
  it('fetches spec data without the language in the query key', async () => {
    const { result } = renderHook(() => useEGOGiftDetailSpec('9001'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current).toBeDefined()
      expect(result.current.tag).toBeDefined()
      expect(result.current.maxEnhancement).toBeDefined()
    })
  })

  it('returns typed spec data', async () => {
    const { result } = renderHook(() => useEGOGiftDetailSpec('9001'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      const spec = result.current
      expect(typeof spec.price).toBe('number')
      expect(Array.isArray(spec.tag)).toBe(true)
      expect(Array.isArray(spec.themePack)).toBe(true)
      expect([0, 1, 2, 3]).toContain(spec.maxEnhancement)
    })
  })
})

describe('useEGOGiftDetailI18n', () => {
  it('fetches i18n data with the language in the query key', async () => {
    const { result } = renderHook(() => useEGOGiftDetailI18n('9001'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current).toBeDefined()
      expect(result.current.name).toBeDefined()
      expect(Array.isArray(result.current.descs)).toBe(true)
    })
  })

  it('returns typed i18n data', async () => {
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
