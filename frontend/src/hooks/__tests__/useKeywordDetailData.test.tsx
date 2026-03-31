import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useKeywordDetailSpec, useKeywordDetailI18n } from '../useKeywordDetailData'

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

describe('useKeywordDetailSpec', () => {
  it('should return spec entry for a known keyword', async () => {
    const { result } = renderHook(() => useKeywordDetailSpec('Aggro'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current).toBeDefined()
      expect(result.current).toHaveProperty('buffType')
      expect(result.current).toHaveProperty('iconId')
      expect(Array.isArray(result.current?.identities)).toBe(true)
      expect(Array.isArray(result.current?.egos)).toBe(true)
      expect(Array.isArray(result.current?.egoGifts)).toBe(true)
    })
  })

  it('should return undefined for unknown keyword', async () => {
    const { result } = renderHook(() => useKeywordDetailSpec('NonExistentKeyword_XYZ'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      // The hook suspends until the query resolves, then returns undefined for missing key
      expect(result.current).toBeUndefined()
    })
  })
})

describe('useKeywordDetailI18n', () => {
  it('should return i18n entry for a known keyword', async () => {
    const { result } = renderHook(() => useKeywordDetailI18n('Aggro'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current).toBeDefined()
      expect(typeof result.current?.name).toBe('string')
      expect(typeof result.current?.desc).toBe('string')
    })
  })

  it('should return undefined for unknown keyword', async () => {
    const { result } = renderHook(() => useKeywordDetailI18n('NonExistentKeyword_XYZ'), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current).toBeUndefined()
    })
  })
})
