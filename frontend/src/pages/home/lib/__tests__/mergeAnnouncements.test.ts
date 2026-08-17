import { describe, it, expect, vi } from 'vitest'

vi.mock('@/lib/formatDate', () => ({
  formatAnnouncementDate: (dateStr: string, language: string) => `${language}:${dateStr}`,
}))

import { mergeAnnouncements } from '../mergeAnnouncements'

import type { AnnouncementI18n, AnnouncementSpec } from '../../types/AnnouncementTypes'

const NOW = new Date('2026-02-20T10:00:00Z')

const i18n: AnnouncementI18n = {
  newest: { title: 'Newest', body: 'Newest body' },
  older: { title: 'Older', body: 'Older body' },
  expired: { title: 'Expired', body: 'Expired body' },
  pinned: { title: 'Pinned', body: 'Pinned body' },
}

function spec(overrides: Partial<AnnouncementSpec> & { id: string }): AnnouncementSpec {
  return { date: '2026-02-01', ...overrides }
}

describe('mergeAnnouncements', () => {
  it('merges a spec with its translation', () => {
    const { announcements } = mergeAnnouncements(
      [spec({ id: 'newest', date: '2026-02-20' })],
      i18n,
      'EN',
      NOW,
    )

    expect(announcements).toEqual([
      {
        id: 'newest',
        date: '2026-02-20',
        formattedDate: 'EN:2026-02-20',
        title: 'Newest',
        body: 'Newest body',
        permanent: false,
      },
    ])
  })

  it.each([
    ['expiry in the past', '2026-02-16', false],
    ['expiry on today (UTC midnight already passed)', '2026-02-20', false],
    ['expiry in the future', '2026-02-21', true],
    ['no expiry', undefined, true],
  ])('%s → kept=%s', (_label, expiresAt, kept) => {
    const { announcements } = mergeAnnouncements(
      [spec({ id: 'expired', date: '2026-02-15', expiresAt })],
      i18n,
      'EN',
      NOW,
    )

    expect(announcements).toHaveLength(kept ? 1 : 0)
  })

  it('reports ids missing from i18n instead of logging', () => {
    const { announcements, missingIds } = mergeAnnouncements(
      [spec({ id: 'newest' }), spec({ id: 'absent' }), spec({ id: 'also-absent' })],
      i18n,
      'EN',
      NOW,
    )

    expect(announcements.map((a) => a.id)).toEqual(['newest'])
    expect(missingIds).toEqual(['absent', 'also-absent'])
  })

  it('orders regular entries newest-first, then permanent entries newest-first', () => {
    const { announcements } = mergeAnnouncements(
      [
        spec({ id: 'older', date: '2026-02-18' }),
        spec({ id: 'pinned', date: '2026-01-01', permanent: true }),
        spec({ id: 'newest', date: '2026-02-20' }),
      ],
      i18n,
      'EN',
      NOW,
    )

    expect(announcements.map((a) => a.id)).toEqual(['newest', 'older', 'pinned'])
  })

  it('returns nothing for an empty spec list', () => {
    expect(mergeAnnouncements([], i18n, 'EN', NOW)).toEqual({ announcements: [], missingIds: [] })
  })
})
