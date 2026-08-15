/**
 * Per-locale coverage for the abbreviated relative-time form: every English
 * unit, the CJK spellings, and the unit boundaries the cascade picks.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

import { formatCompactRelativeTime } from '../formatDate'

const NOW = new Date('2024-12-31T15:00:00Z')

const SECOND = 1_000
const MINUTE = 60 * SECOND
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/** ISO string for a timestamp `ms` before the frozen now. */
const ago = (ms: number) => new Date(NOW.getTime() - ms).toISOString()

describe('formatCompactRelativeTime', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('abbreviates every English unit', () => {
    const at = (ms: number) => formatCompactRelativeTime(ago(ms), 'EN')

    expect(at(0)).toBe('0s ago')
    expect(at(5 * SECOND)).toBe('5s ago')
    expect(at(23 * MINUTE)).toBe('23m ago')
    expect(at(2 * HOUR)).toBe('2h ago')
    expect(at(3 * DAY)).toBe('3d ago')
    expect(at(5 * 31 * DAY)).toBe('5mo ago')
    expect(at(3 * 366 * DAY)).toBe('3y ago')
  })

  it('renders the Japanese units', () => {
    const at = (ms: number) => formatCompactRelativeTime(ago(ms), 'JP')

    expect(at(23 * MINUTE)).toBe('23分前')
    expect(at(2 * HOUR)).toBe('2時間前')
    expect(at(5 * 31 * DAY)).toBe('5か月前')
    expect(at(3 * 366 * DAY)).toBe('3年前')
  })

  it('renders the Chinese units', () => {
    const at = (ms: number) => formatCompactRelativeTime(ago(ms), 'CN')

    expect(at(5 * SECOND)).toBe('5秒前')
    expect(at(23 * MINUTE)).toBe('23分钟前')
    expect(at(2 * HOUR)).toBe('2小时前')
    expect(at(3 * DAY)).toBe('3天前')
    expect(at(5 * 31 * DAY)).toBe('5个月前')
    expect(at(3 * 366 * DAY)).toBe('3年前')
  })

  it('leaves Korean unabbreviated', () => {
    expect(formatCompactRelativeTime(ago(23 * MINUTE), 'KR')).toBe('23분 전')
    expect(formatCompactRelativeTime(ago(5 * 31 * DAY), 'KR')).toBe('5개월 전')
  })

  it('keeps the numeric form a day out rather than saying "yesterday"', () => {
    expect(formatCompactRelativeTime(ago(DAY), 'EN')).toBe('1d ago')
  })

  it('steps up a unit only once the span fills it', () => {
    const at = (ms: number) => formatCompactRelativeTime(ago(ms), 'EN')

    expect(at(59 * SECOND)).toBe('59s ago')
    expect(at(MINUTE)).toBe('1m ago')
    expect(at(23 * HOUR)).toBe('23h ago')
    expect(at(30 * DAY)).toBe('30d ago')
    expect(at(31 * DAY)).toBe('1mo ago')
    expect(at(365 * DAY)).toBe('11mo ago')
    expect(at(366 * DAY)).toBe('1y ago')
  })

  it('renders future timestamps in the same units', () => {
    const ahead = new Date(NOW.getTime() + 10 * MINUTE).toISOString()

    expect(formatCompactRelativeTime(ahead, 'EN')).toBe('in 10m')
  })

  it('falls back to the runtime locale for unmapped language codes', () => {
    const runtimeDefault = formatCompactRelativeTime(ago(2 * HOUR))

    expect(formatCompactRelativeTime(ago(2 * HOUR), 'zz')).toBe(runtimeDefault)
    expect(formatCompactRelativeTime(ago(2 * HOUR), '')).toBe(runtimeDefault)
  })

  it('accepts a BCP 47 locale as well as an app language code', () => {
    expect(formatCompactRelativeTime(ago(23 * MINUTE), 'ko-KR')).toBe(
      formatCompactRelativeTime(ago(23 * MINUTE), 'KR'),
    )
  })
})
