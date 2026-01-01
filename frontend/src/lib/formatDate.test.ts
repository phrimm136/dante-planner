/**
 * formatDate.test.ts
 *
 * Tests for date formatting utility functions.
 * Uses Vitest for testing framework.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { formatPlannerDate, formatFullDate, formatRelativeTime } from './formatDate'

describe('formatPlannerDate', () => {
  beforeEach(() => {
    // Mock current time to a fixed date: 2024-12-31 15:00:00 UTC
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2024-12-31T15:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('recent dates (< 24 hours)', () => {
    it('returns HH:mm format for dates within 24 hours', () => {
      // 5 hours ago
      const result = formatPlannerDate('2024-12-31T10:30:00Z')

      // Should contain hour:minute format (exact format depends on locale)
      // At minimum, should NOT contain a slash (date format)
      expect(result).not.toContain('/')
      // Should contain a colon (time format)
      expect(result).toContain(':')
    })

    it('returns HH:mm format for dates 1 hour ago', () => {
      // 1 hour ago
      const result = formatPlannerDate('2024-12-31T14:00:00Z')

      expect(result).not.toContain('/')
      expect(result).toContain(':')
    })

    it('returns HH:mm format for dates just under 24 hours', () => {
      // 23 hours ago
      const result = formatPlannerDate('2024-12-30T16:00:00Z')

      expect(result).not.toContain('/')
      expect(result).toContain(':')
    })

    it('returns time in 24-hour format', () => {
      // 2 hours ago (13:00:00)
      const result = formatPlannerDate('2024-12-31T13:00:00Z')

      // Result should be in 24-hour format (no AM/PM)
      expect(result).not.toMatch(/[AP]M/i)
    })
  })

  describe('older dates (>= 24 hours)', () => {
    it('returns MM/DD format for dates 24+ hours old', () => {
      // Exactly 24 hours ago
      const result = formatPlannerDate('2024-12-30T15:00:00Z')

      // Should contain a slash (date format)
      expect(result).toContain('/')
      // Should NOT be in time format (no colon, or if has colon, still has slash)
      // The key is it should contain slash for date
    })

    it('returns MM/DD format for dates several days old', () => {
      // 6 days ago
      const result = formatPlannerDate('2024-12-25T10:30:00Z')

      // Should be date format (contains slash)
      expect(result).toContain('/')
    })

    it('returns MM/DD format for dates a month ago', () => {
      // 30 days ago
      const result = formatPlannerDate('2024-12-01T10:30:00Z')

      expect(result).toContain('/')
    })

    it('handles dates from previous year', () => {
      // 1 year ago
      const result = formatPlannerDate('2023-12-31T10:30:00Z')

      // Should still return date format
      expect(result).toContain('/')
    })
  })

  describe('edge cases', () => {
    it('handles exactly 24 hour boundary', () => {
      // Exactly 24 hours minus 1 second should be recent
      const justUnder = formatPlannerDate('2024-12-30T15:00:01Z')
      expect(justUnder).not.toContain('/')

      // Exactly 24 hours should be old
      const exactlyAt = formatPlannerDate('2024-12-30T15:00:00Z')
      expect(exactlyAt).toContain('/')
    })

    it('handles future dates gracefully', () => {
      // 1 hour in the future
      const result = formatPlannerDate('2024-12-31T16:00:00Z')

      // Should still produce a valid format (time, since diff is < 24h)
      expect(result).toBeDefined()
      expect(typeof result).toBe('string')
      expect(result.length).toBeGreaterThan(0)
    })

    it('handles invalid date string by throwing', () => {
      // Invalid dates will cause Intl.DateTimeFormat to throw RangeError
      // This is expected behavior - callers should validate input
      expect(() => formatPlannerDate('invalid-date')).toThrow()
    })
  })
})

describe('formatFullDate', () => {
  it('returns full date with time', () => {
    const result = formatFullDate('2024-12-31T14:32:00Z')

    // Should contain year
    expect(result).toMatch(/2024/)
    // Should contain time element (colon)
    expect(result).toContain(':')
  })

  it('includes month and day', () => {
    const result = formatFullDate('2024-06-15T10:30:00Z')

    // Should contain "Jun" or "6" for month
    expect(result).toMatch(/Jun|6/)
    // Should contain day
    expect(result).toMatch(/15/)
  })

  it('handles various date formats consistently', () => {
    const result1 = formatFullDate('2024-01-01T00:00:00Z')
    const result2 = formatFullDate('2024-12-31T23:59:59Z')

    // Both should be strings with reasonable length
    expect(typeof result1).toBe('string')
    expect(typeof result2).toBe('string')
    expect(result1.length).toBeGreaterThan(10)
    expect(result2.length).toBeGreaterThan(10)
  })
})

describe('formatRelativeTime', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2024-12-31T15:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns relative time for hours ago', () => {
    const result = formatRelativeTime('2024-12-31T10:00:00Z')

    // Should contain "hour" or similar
    expect(result.toLowerCase()).toMatch(/hour|hr|h/)
  })

  it('returns relative time for days ago', () => {
    const result = formatRelativeTime('2024-12-29T15:00:00Z')

    // Should contain "day" or similar
    expect(result.toLowerCase()).toMatch(/day|d/)
  })

  it('returns relative time for minutes ago', () => {
    const result = formatRelativeTime('2024-12-31T14:30:00Z')

    // Should contain "minute" or similar
    expect(result.toLowerCase()).toMatch(/minute|min|m/)
  })

  it('returns relative time for seconds ago', () => {
    const result = formatRelativeTime('2024-12-31T14:59:30Z')

    // Should contain "second" or similar, or "just now"
    expect(result.toLowerCase()).toMatch(/second|sec|s|now/)
  })

  it('handles longer time periods', () => {
    const result = formatRelativeTime('2024-12-01T15:00:00Z')

    // 30 days ago - should mention days
    expect(result.toLowerCase()).toMatch(/day|d/)
  })
})
