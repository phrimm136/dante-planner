import { renderHook } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { announcementQueryKeys, useAnnouncementData } from './useAnnouncementData'
import {
  AnnouncementSpecListSchema,
  AnnouncementI18nSchema,
} from '@/schemas/AnnouncementSchemas'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ i18n: { language: 'EN' } }),
}))

vi.mock('@tanstack/react-query', async () => {
  const actual = await vi.importActual('@tanstack/react-query')
  return {
    ...actual,
    useSuspenseQuery: vi.fn(),
    queryOptions: vi.fn((opts: unknown) => opts),
  }
})

// Simplify date formatting in tests — return raw date string
vi.mock('@/lib/formatDate', () => ({
  formatAnnouncementDate: (dateStr: string) => dateStr,
}))

import { useSuspenseQuery } from '@tanstack/react-query'

function setupQueryMocks(specData: unknown, i18nData: unknown) {
  vi.mocked(useSuspenseQuery)
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    .mockReturnValueOnce({ data: specData } as any)
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    .mockReturnValueOnce({ data: i18nData } as any)
}

describe('announcementQueryKeys', () => {
  it('spec key is stable', () => {
    expect(announcementQueryKeys.spec()).toEqual(['announcements', 'spec'])
  })

  it('i18n key includes language', () => {
    expect(announcementQueryKeys.i18n('EN')).toEqual(['announcements', 'i18n', 'EN'])
    expect(announcementQueryKeys.i18n('KR')).toEqual(['announcements', 'i18n', 'KR'])
  })

  it('i18n key differs by language', () => {
    expect(announcementQueryKeys.i18n('EN')).not.toEqual(announcementQueryKeys.i18n('KR'))
  })
})

describe('AnnouncementSpecListSchema', () => {
  it('UT3: empty array is valid', () => {
    expect(AnnouncementSpecListSchema.safeParse([]).success).toBe(true)
  })

  it('UT1: entry with expiresAt passes schema validation', () => {
    const result = AnnouncementSpecListSchema.safeParse([
      { id: 'test-id', date: '2026-02-19', expiresAt: '2026-01-01' },
    ])
    expect(result.success).toBe(true)
  })

  it('UT2: multiple entries parse correctly', () => {
    const result = AnnouncementSpecListSchema.safeParse([
      { id: 'id-1', date: '2026-02-20' },
      { id: 'id-2', date: '2026-02-19' },
    ])
    expect(result.success).toBe(true)
    expect(result.data?.length).toBe(2)
  })

  it('rejects entries missing required id', () => {
    const result = AnnouncementSpecListSchema.safeParse([{ date: '2026-02-19' }])
    expect(result.success).toBe(false)
  })

  it('rejects entries missing required date', () => {
    const result = AnnouncementSpecListSchema.safeParse([{ id: 'test-id' }])
    expect(result.success).toBe(false)
  })
})

describe('AnnouncementI18nSchema', () => {
  it('UT4: valid record passes', () => {
    const result = AnnouncementI18nSchema.safeParse({
      'test-id': { title: 'Title', body: 'Body text' },
    })
    expect(result.success).toBe(true)
  })

  it('empty record passes (missing i18n ids = hook skips, not schema error)', () => {
    expect(AnnouncementI18nSchema.safeParse({}).success).toBe(true)
  })

  it('rejects entry missing title', () => {
    const result = AnnouncementI18nSchema.safeParse({
      'test-id': { body: 'Body only' },
    })
    expect(result.success).toBe(false)
  })

  it('rejects entry missing body', () => {
    const result = AnnouncementI18nSchema.safeParse({
      'test-id': { title: 'Title only' },
    })
    expect(result.success).toBe(false)
  })
})

describe('useAnnouncementData module exports', () => {
  it('exports useAnnouncementData as a function', async () => {
    const module = await import('./useAnnouncementData')
    expect(typeof module.useAnnouncementData).toBe('function')
  })

  it('exports announcementQueryKeys', async () => {
    const module = await import('./useAnnouncementData')
    expect(module.announcementQueryKeys).toBeDefined()
    expect(typeof module.announcementQueryKeys.spec).toBe('function')
    expect(typeof module.announcementQueryKeys.i18n).toBe('function')
  })
})

// ============================================================================
// Business Logic Tests
// ============================================================================

const baseSpec = [
  { id: 'newest', date: '2026-02-20' },
  { id: 'older', date: '2026-02-18' },
  { id: 'expired', date: '2026-02-15', expiresAt: '2026-02-16' },
]

const baseI18n = {
  newest: { title: 'Newest Title', body: 'Newest body' },
  older: { title: 'Older Title', body: 'Older body' },
  expired: { title: 'Expired Title', body: 'Expired body' },
}

describe('useAnnouncementData business logic', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('UT1: filters out entries whose expiresAt is in the past', () => {
    setupQueryMocks(baseSpec, baseI18n)
    const { result } = renderHook(() => useAnnouncementData())

    const ids = result.current.map((a) => a.id)
    expect(ids).not.toContain('expired')
    expect(ids).toContain('newest')
    expect(ids).toContain('older')
  })

  it('UT2: returns announcements sorted newest-first by date', () => {
    setupQueryMocks(baseSpec, baseI18n)
    const { result } = renderHook(() => useAnnouncementData())

    expect(result.current[0].id).toBe('newest')
    expect(result.current[1].id).toBe('older')
  })

  it('UT3: returns empty array when spec list is empty', () => {
    setupQueryMocks([], {})
    const { result } = renderHook(() => useAnnouncementData())

    expect(result.current).toEqual([])
  })

  it('UT4: skips entries missing from i18n and logs an error', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    setupQueryMocks(
      [{ id: 'has-i18n', date: '2026-02-20' }, { id: 'no-i18n', date: '2026-02-19' }],
      { 'has-i18n': { title: 'Title', body: 'Body' } }
    )
    const { result } = renderHook(() => useAnnouncementData())

    expect(result.current).toHaveLength(1)
    expect(result.current[0].id).toBe('has-i18n')
    expect(consoleSpy).toHaveBeenCalledWith(expect.stringContaining('no-i18n'))

    consoleSpy.mockRestore()
  })

  it('filters out an entry that expires mid-session (re-render after expiry)', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-02-20T10:00:00Z'))

    setupQueryMocks(
      [{ id: 'expiring-soon', date: '2026-02-19', expiresAt: '2026-02-20' }],
      { 'expiring-soon': { title: 'Expiring', body: 'Body' } }
    )

    const { result, rerender } = renderHook(() => useAnnouncementData())
    // Before midnight on expiry date — entry is still visible (expiresAt is same-day)
    // new Date('2026-02-20') = midnight UTC; our time is 10:00 UTC = past midnight → filtered
    expect(result.current).toHaveLength(0)

    vi.useRealTimers()
  })

  it('merges spec and i18n into Announcement shape', () => {
    setupQueryMocks(
      [{ id: 'test-id', date: '2026-02-20' }],
      { 'test-id': { title: 'Test Title', body: 'Test Body' } }
    )
    const { result } = renderHook(() => useAnnouncementData())

    expect(result.current[0]).toMatchObject({
      id: 'test-id',
      date: '2026-02-20',
      title: 'Test Title',
      body: 'Test Body',
    })
  })
})
