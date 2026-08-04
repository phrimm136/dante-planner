/**
 * Parity for the per-locale abbreviation table against the replace chain it
 * replaced, which ran every language's rules over every language's output.
 *
 * The old chain is transcribed verbatim below and applied to the same date-fns
 * phrase, so a rule that quietly stopped firing — or one that fires for a
 * locale it should not — shows up as a mismatch.
 */

import { describe, it, expect } from 'vitest'
import { formatDistanceToNowStrict, type Locale } from 'date-fns'
import { enUS, ja, ko, zhCN } from 'date-fns/locale'

import { formatShortRelativeTime } from '../utils'

/** Verbatim transcription of the pre-table implementation. */
function legacyShortRelativeTime(date: Date, locale: Locale): string {
  const distance = formatDistanceToNowStrict(date, { locale, addSuffix: true })
  return distance
    .replace(/(\d+)\s*seconds?\s*ago/, '$1s ago')
    .replace(/(\d+)\s*minutes?\s*ago/, '$1m ago')
    .replace(/(\d+)\s*hours?\s*ago/, '$1h ago')
    .replace(/(\d+)\s*days?\s*ago/, '$1d ago')
    .replace(/(\d+)\s*months?\s*ago/, '$1mo ago')
    .replace(/(\d+)\s*years?\s*ago/, '$1y ago')
    .replace(/(\d+)か月前/, '$1ヶ月前')
    .replace(/(\d+)\s*秒钟?前/, '$1秒前')
    .replace(/(\d+)\s*分钟前/, '$1分前')
    .replace(/(\d+)\s*小时前/, '$1时前')
    .replace(/(\d+)\s*天前/, '$1天前')
    .replace(/(\d+)\s*个月前/, '$1月前')
    .replace(/(\d+)\s*年前/, '$1年前')
}

const SECOND = 1_000
const MINUTE = 60 * SECOND
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/** One delta per unit plus each unit's singular/plural and rounding boundaries. */
const DELTAS_MS = [
  0,
  SECOND,
  5 * SECOND,
  44 * SECOND,
  45 * SECOND,
  59 * SECOND,
  MINUTE,
  2 * MINUTE,
  23 * MINUTE,
  59 * MINUTE,
  HOUR,
  2 * HOUR,
  23 * HOUR,
  DAY,
  2 * DAY,
  6 * DAY,
  29 * DAY,
  30 * DAY,
  31 * DAY,
  45 * DAY,
  5 * 31 * DAY,
  11 * 30 * DAY,
  365 * DAY,
  366 * DAY,
  3 * 366 * DAY,
  10 * 366 * DAY,
]

/** Mapped app languages, plus unmapped ones that must fall back to en-US rules. */
const LANGUAGES: Array<[string, Locale]> = [
  ['EN', enUS],
  ['KR', ko],
  ['JP', ja],
  ['CN', zhCN],
  ['en-US', enUS],
  ['ko-KR', enUS],
  ['zz', enUS],
  ['', enUS],
]

describe('formatShortRelativeTime', () => {
  it.each(LANGUAGES)('matches the legacy replace chain for %s', (language, locale) => {
    const now = Date.now()
    for (const deltaMs of DELTAS_MS) {
      const date = new Date(now - deltaMs)
      expect(formatShortRelativeTime(date, language)).toBe(legacyShortRelativeTime(date, locale))
    }
  })

  it('abbreviates every English unit', () => {
    const now = Date.now()
    const at = (ms: number) => formatShortRelativeTime(new Date(now - ms), 'EN')

    expect(at(5 * SECOND)).toBe('5s ago')
    expect(at(23 * MINUTE)).toBe('23m ago')
    expect(at(2 * HOUR)).toBe('2h ago')
    expect(at(3 * DAY)).toBe('3d ago')
    expect(at(5 * 31 * DAY)).toBe('5mo ago')
    expect(at(3 * 366 * DAY)).toBe('3y ago')
  })

  it('keeps the Japanese ヶ月 spelling CLDR does not produce', () => {
    expect(formatShortRelativeTime(new Date(Date.now() - 5 * 31 * DAY), 'JP')).toBe('5ヶ月前')
  })

  it('clips the Chinese units', () => {
    const now = Date.now()
    const at = (ms: number) => formatShortRelativeTime(new Date(now - ms), 'CN')

    expect(at(23 * MINUTE)).toBe('23分前')
    expect(at(2 * HOUR)).toBe('2时前')
    expect(at(5 * 31 * DAY)).toBe('5月前')
  })

  it('leaves Korean unabbreviated', () => {
    const now = Date.now()
    expect(formatShortRelativeTime(new Date(now - 23 * MINUTE), 'KR')).toBe('23분 전')
    expect(formatShortRelativeTime(new Date(now - 5 * 31 * DAY), 'KR')).toBe('5개월 전')
  })

  it('does not leak one locale rule into another locale output', () => {
    // The old chain ran the Chinese 年前 rule over Japanese text and vice versa;
    // scoping the rules must not change what either renders.
    const now = Date.now()
    expect(formatShortRelativeTime(new Date(now - 3 * 366 * DAY), 'JP')).toBe('3年前')
    expect(formatShortRelativeTime(new Date(now - 3 * 366 * DAY), 'CN')).toBe('3年前')
  })

  it('accepts an ISO string as well as a Date', () => {
    const iso = new Date(Date.now() - 2 * HOUR).toISOString()
    expect(formatShortRelativeTime(iso, 'EN')).toBe('2h ago')
  })
})
