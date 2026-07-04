import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  useKeywordListSpec,
  useKeywordListI18n,
  useKeywordListI18nDeferred,
} from '../useKeywordListData'

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

describe('useKeywordListSpec', () => {
  it('should fetch spec data and return a record', async () => {
    const { result } = renderHook(() => useKeywordListSpec(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current).toBeDefined()
      expect(typeof result.current).toBe('object')
    })
  })

  it('should return entries with correct shape', async () => {
    const { result } = renderHook(() => useKeywordListSpec(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      const keys = Object.keys(result.current)
      expect(keys.length).toBeGreaterThan(0)

      const firstKey = keys[0]
      const entry = result.current[firstKey]
      expect(entry).toHaveProperty('iconId')
      expect(entry).toHaveProperty('buffType')
      expect(Array.isArray(entry.identities)).toBe(true)
      expect(Array.isArray(entry.egos)).toBe(true)
      expect(Array.isArray(entry.egoGifts)).toBe(true)
    })
  })
})

describe('useKeywordListI18n', () => {
  it('should fetch i18n data with language key', async () => {
    const { result } = renderHook(() => useKeywordListI18n(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current).toBeDefined()
      expect(typeof result.current).toBe('object')
    })
  })

  it('should return entries with name and desc', async () => {
    const { result } = renderHook(() => useKeywordListI18n(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      const keys = Object.keys(result.current)
      expect(keys.length).toBeGreaterThan(0)

      const firstKey = keys[0]
      const entry = result.current[firstKey]
      expect(typeof entry.name).toBe('string')
      expect(typeof entry.desc).toBe('string')
    })
  })
})

describe('useKeywordListI18nDeferred', () => {
  it('should return empty object initially then populate', async () => {
    const { result } = renderHook(() => useKeywordListI18nDeferred(), {
      wrapper: createWrapper(),
    })

    // Initial state should be an object (possibly empty)
    expect(typeof result.current).toBe('object')

    await waitFor(() => {
      const keys = Object.keys(result.current)
      expect(keys.length).toBeGreaterThan(0)
    })
  })

  it('should return entries with name and desc after loading', async () => {
    const { result } = renderHook(() => useKeywordListI18nDeferred(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      const keys = Object.keys(result.current)
      expect(keys.length).toBeGreaterThan(0)

      const firstKey = keys[0]
      const entry = result.current[firstKey]
      expect(typeof entry.name).toBe('string')
      expect(typeof entry.desc).toBe('string')
    })
  })
})
