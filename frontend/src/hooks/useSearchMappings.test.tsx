/**
 * useSearchMappings.test.tsx
 *
 * Tests for search mapping hooks:
 * - useSearchMappings: suspending version
 * - useSearchMappingsDeferred: non-suspending version for list filtering
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { useSearchMappingsDeferred } from './useSearchMappings'

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    i18n: { language: 'EN' },
  }),
}))

// Mock fetch
const mockKeywordMatch = {
  Burst: 'Rupture',
  Combustion: 'Burn',
  Charge: 'Charge',
}

const mockUnitKeywords = {
  BLADE_LINEAGE: 'Blade Lineage',
  FINGER: 'Finger',
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0, // Disable garbage collection for tests
      },
    },
  })

  return {
    wrapper: function Wrapper({ children }: { children: React.ReactNode }) {
      return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    },
    queryClient,
  }
}

describe('useSearchMappingsDeferred', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns empty mappings initially (non-suspending)', async () => {
    // Mock fetch to return after a delay
    vi.spyOn(global, 'fetch').mockImplementation(() =>
      new Promise((resolve) =>
        setTimeout(
          () =>
            resolve({
              ok: true,
              status: 200,
              json: () => Promise.resolve(mockKeywordMatch),
            } as Response),
          100
        )
      )
    )

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSearchMappingsDeferred(), { wrapper })

    // Should return empty mappings immediately (not suspend)
    expect(result.current.keywordToValue.size).toBe(0)
    expect(result.current.unitKeywordToValue.size).toBe(0)
  })

  it('populates mappings after data loads', async () => {
    vi.spyOn(global, 'fetch').mockImplementation((url) => {
      const urlStr = url.toString()
      if (urlStr.includes('keywordMatch')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(mockKeywordMatch),
        } as Response)
      }
      if (urlStr.includes('unitKeywords')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(mockUnitKeywords),
        } as Response)
      }
      return Promise.reject(new Error('Unknown URL'))
    })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSearchMappingsDeferred(), { wrapper })

    // Wait for data to load
    await waitFor(() => {
      expect(result.current.keywordToValue.size).toBeGreaterThan(0)
    })

    // Verify keyword mappings (display name -> internal codes)
    expect(result.current.keywordToValue.get('rupture')).toContain('Burst')
    expect(result.current.keywordToValue.get('burn')).toContain('Combustion')
    expect(result.current.keywordToValue.get('charge')).toContain('Charge')

    // Verify unit keyword mappings
    expect(result.current.unitKeywordToValue.get('blade lineage')).toContain('BLADE_LINEAGE')
    expect(result.current.unitKeywordToValue.get('finger')).toContain('FINGER')
  })

  it('handles 404 gracefully (returns empty)', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValue({
      ok: false,
      status: 404,
      json: () => Promise.resolve({}),
    } as Response)

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSearchMappingsDeferred(), { wrapper })

    // Should return empty mappings
    await waitFor(() => {
      expect(result.current.keywordToValue.size).toBe(0)
    })
  })

  it('creates reverse mappings correctly (lowercase display -> internal)', async () => {
    const customKeywords = {
      SomePascalCase: 'Display Name With Spaces',
      ALLCAPS: 'Another Display',
    }

    vi.spyOn(global, 'fetch').mockImplementation((url) => {
      const urlStr = url.toString()
      if (urlStr.includes('keywordMatch')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(customKeywords),
        } as Response)
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({}),
      } as Response)
    })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSearchMappingsDeferred(), { wrapper })

    await waitFor(() => {
      expect(result.current.keywordToValue.size).toBeGreaterThan(0)
    })

    // Keys should be lowercase
    expect(result.current.keywordToValue.get('display name with spaces')).toContain('SomePascalCase')
    expect(result.current.keywordToValue.get('another display')).toContain('ALLCAPS')

    // Original case should not exist as key
    expect(result.current.keywordToValue.get('Display Name With Spaces')).toBeUndefined()
  })

  it('handles multiple internal codes mapping to same display name', async () => {
    const duplicateDisplayKeywords = {
      Code1: 'Same Display',
      Code2: 'Same Display',
      Code3: 'Different',
    }

    vi.spyOn(global, 'fetch').mockImplementation((url) => {
      const urlStr = url.toString()
      if (urlStr.includes('keywordMatch')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(duplicateDisplayKeywords),
        } as Response)
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({}),
      } as Response)
    })

    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSearchMappingsDeferred(), { wrapper })

    await waitFor(() => {
      expect(result.current.keywordToValue.size).toBeGreaterThan(0)
    })

    // Both codes should be in the array for the same display name
    const sameDisplayCodes = result.current.keywordToValue.get('same display')
    expect(sameDisplayCodes).toContain('Code1')
    expect(sameDisplayCodes).toContain('Code2')
    expect(sameDisplayCodes).toHaveLength(2)
  })
})
