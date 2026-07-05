import React from 'react'
import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useBattleKeywords, getKeywordName } from '../useBattleKeywords'

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

describe('useBattleKeywords', () => {
  it('should return merged data with name, desc, iconId, buffType', async () => {
    const { result } = renderHook(() => useBattleKeywords(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      expect(result.current.data).toBeDefined()
      const keys = Object.keys(result.current.data)
      expect(keys.length).toBeGreaterThan(0)
    })
  })

  it('should produce entries with the correct merged shape', async () => {
    const { result } = renderHook(() => useBattleKeywords(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      const keys = Object.keys(result.current.data)
      expect(keys.length).toBeGreaterThan(0)

      const firstKey = keys[0]
      const entry = result.current.data[firstKey]
      expect(typeof entry.name).toBe('string')
      expect(typeof entry.desc).toBe('string')
      expect(entry).toHaveProperty('iconId')
      expect(typeof entry.buffType).toBe('string')
    })
  })

  it('should have iconId as string or null', async () => {
    const { result } = renderHook(() => useBattleKeywords(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      const entries = Object.values(result.current.data) as Array<{ iconId: string | null }>
      for (const entry of entries.slice(0, 10)) {
        expect(entry.iconId === null || typeof entry.iconId === 'string').toBe(true)
      }
    })
  })

  it('forwards optional `flavor` from i18n to the merged entry', async () => {
    // Regression guard for a previous bug where the merge enumerated fields
    // and silently dropped `flavor`. RyoshuEGOPF is one of the ~20 keywords
    // that ship a non-empty flavor lore line in the built data.
    const { result } = renderHook(() => useBattleKeywords(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      const entry = result.current.data['RyoshuEGOPF'] as
        | { name: string; desc: string; flavor?: string }
        | undefined
      // The data file may or may not include RyoshuEGOPF depending on pipeline state,
      // so we assert structurally: every entry that has a flavor must keep it through
      // the merge.
      const entries = Object.values(result.current.data) as Array<{ flavor?: string }>
      const flavored = entries.filter((e) => typeof e.flavor === 'string' && e.flavor.length > 0)
      // At least the dataset known to ship flavor must survive the merge.
      expect(flavored.length).toBeGreaterThan(0)

      if (entry) {
        expect(typeof entry.flavor === 'string' || entry.flavor === undefined).toBe(true)
      }
    })
  })

  it('preserves entries without flavor (most keywords)', async () => {
    const { result } = renderHook(() => useBattleKeywords(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => {
      const entries = Object.values(result.current.data) as Array<{ flavor?: string }>
      const unflavored = entries.filter((e) => !e.flavor)
      // Vast majority of keywords have no flavor.
      expect(unflavored.length).toBeGreaterThan(0)
    })
  })
})

describe('getKeywordName', () => {
  it('should return translated name when keyword exists', () => {
    const keywords = {
      Burn: { name: 'Burn', desc: 'Deals damage.', iconId: 'Burn', buffType: 'Negative' },
    }
    expect(getKeywordName(keywords, 'Burn')).toBe('Burn')
  })

  it('should return original key when keyword does not exist', () => {
    expect(getKeywordName({}, 'UnknownKeyword')).toBe('UnknownKeyword')
  })
})
