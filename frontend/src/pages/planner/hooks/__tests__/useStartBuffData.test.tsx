/**
 * Runs the hook against the real static JSON — the shipped data serializes
 * baseId as a string, so these tests fail unless the loader routes the module
 * through the schema instead of casting it.
 *
 * Mocking policy: only react-i18next is faked (language pin); the static data
 * imports and schemas are real.
 */

import { Suspense } from 'react'
import type { ReactNode } from 'react'
import { renderHook, waitFor } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { QueryClientProvider } from '@tanstack/react-query'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { BASE_BUFF_IDS } from '@/shared/gameText'
import { useStartBuffData, getBaseBuffs, getBuffById } from '../useStartBuffData'

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
  const queryClient = createTestQueryClient()
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>{children}</Suspense>
      </QueryClientProvider>
    )
  }
}

async function renderBuffs(version: number) {
  const { result } = renderHook(() => useStartBuffData(version), {
    wrapper: createWrapper(),
  })
  await waitFor(() => expect(result.current).not.toBeNull())
  return result.current!.data
}

describe('useStartBuffData', () => {
  it.each([6, 7])('MD%i: parses every buff with a numeric baseId', async (version) => {
    const data = await renderBuffs(version)

    expect(data).toHaveLength(30)
    for (const buff of data) {
      expect(typeof buff.baseId).toBe('number')
      expect(typeof buff.level).toBe('number')
    }
  })

  it.each([6, 7])('MD%i: getBaseBuffs returns the ten level-1 buffs', async (version) => {
    const data = await renderBuffs(version)
    const baseBuffs = getBaseBuffs(data)

    expect(baseBuffs).toHaveLength(BASE_BUFF_IDS.length)
    expect(baseBuffs.every((b) => b.level === 1)).toBe(true)
    const byValue = (a: number, b: number) => a - b
    expect(baseBuffs.map((b) => b.baseId).sort(byValue)).toEqual([...BASE_BUFF_IDS].sort(byValue))
  })

  it('getBuffById resolves an enhanced buff by its full id', async () => {
    const data = await renderBuffs(7)
    const buff = getBuffById(data, 201)

    expect(buff).toBeDefined()
    expect(buff!.level).toBe(2)
    expect(buff!.baseId).toBe(101)
  })

  it('resolves buff names through i18n', async () => {
    const data = await renderBuffs(7)

    for (const buff of getBaseBuffs(data)) {
      expect(buff.name).not.toBe('')
    }
  })
})
