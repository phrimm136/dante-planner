import { describe, it, expect } from 'vitest'
import { formatConditionLabel } from '../AbEventChoiceBranch'

// Mock t() that returns the key with interpolated values
function mockT(key: string, options?: Record<string, unknown>): string {
  if (options) {
    return `${key}:${JSON.stringify(options)}`
  }
  return key
}

describe('formatConditionLabel', () => {
  describe('empty/unknown conditions', () => {
    it('returns empty string for empty condition', () => {
      expect(formatConditionLabel('', mockT)).toBe('')
    })

    it('returns raw condition for unknown pattern', () => {
      expect(formatConditionLabel('SomeUnknownCondition', mockT)).toBe('SomeUnknownCondition')
    })
  })

  describe('Prob_ conditions', () => {
    it('formats Prob_0.5 as 50%', () => {
      const result = formatConditionLabel('Prob_0.5', mockT)
      expect(result).toContain('condProb')
      expect(result).toContain('"value":50')
    })

    it('formats Prob_1 as 100%', () => {
      const result = formatConditionLabel('Prob_1', mockT)
      expect(result).toContain('"value":100')
    })

    it('formats Prob_0.3 as 30%', () => {
      const result = formatConditionLabel('Prob_0.3', mockT)
      expect(result).toContain('"value":30')
    })
  })

  describe('ProbTimesRepeatCount_ conditions (cumulative)', () => {
    it('formats positive value as cumulative percentage', () => {
      const result = formatConditionLabel('ProbTimesRepeatCount_0.125', mockT)
      expect(result).toContain('condProbCumulative')
      expect(result).toContain('"value":"12.5"')
    })

    it('formats negative value as failure/retry', () => {
      const result = formatConditionLabel('ProbTimesRepeatCount_-0.125', mockT)
      expect(result).toContain('condProbCumulativeFail')
    })

    it('formats integer percentage without decimal', () => {
      const result = formatConditionLabel('ProbTimesRepeatCount_0.5', mockT)
      expect(result).toContain('"value":50')
    })
  })

  describe('MpAverage_ conditions', () => {
    it('formats MpAverage_Under0', () => {
      const result = formatConditionLabel('MpAverage_Under0', mockT)
      expect(result).toContain('condMpAverageUnder')
      expect(result).toContain('"value":"0"')
    })

    it('formats MpAverage_NotLessThan25', () => {
      const result = formatConditionLabel('MpAverage_NotLessThan25', mockT)
      expect(result).toContain('condMpAverageNotLessThan')
      expect(result).toContain('"value":"25"')
    })
  })

  describe('Failed_ conditions', () => {
    it('formats Failed_Under3', () => {
      const result = formatConditionLabel('Failed_Under3', mockT)
      expect(result).toContain('condFailedUnder')
      expect(result).toContain('"value":"3"')
    })

    it('formats Failed_NotLessThan3', () => {
      const result = formatConditionLabel('Failed_NotLessThan3', mockT)
      expect(result).toContain('condFailedNotLessThan')
      expect(result).toContain('"value":"3"')
    })
  })
})
