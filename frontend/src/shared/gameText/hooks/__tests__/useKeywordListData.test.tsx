import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useKeywordListSpec, useKeywordListI18n } from '../useKeywordListData'

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
      const entries = Object.entries(result.current)
      expect(entries.length).toBeGreaterThan(0)

      const entry = entries[0]?.[1]
      expect(entry).toHaveProperty('iconId')
      expect(entry).toHaveProperty('buffType')
      expect(Array.isArray(entry?.identities)).toBe(true)
      expect(Array.isArray(entry?.egos)).toBe(true)
      expect(Array.isArray(entry?.egoGifts)).toBe(true)
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
      const entries = Object.entries(result.current)
      expect(entries.length).toBeGreaterThan(0)

      const entry = entries[0]?.[1]
      expect(typeof entry?.name).toBe('string')
      expect(typeof entry?.desc).toBe('string')
    })
  })
})
