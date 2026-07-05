import { describe, it, expect } from 'vitest'
import { areSetsEqual } from '../setUtils'

describe('areSetsEqual', () => {
  it('returns true for two empty sets', () => {
    expect(areSetsEqual(new Set(), new Set())).toBe(true)
  })

  it('returns true for sets with the same members', () => {
    expect(areSetsEqual(new Set(['a', 'b']), new Set(['b', 'a']))).toBe(true)
  })

  it('returns false when sizes differ (subset)', () => {
    expect(areSetsEqual(new Set(['a']), new Set(['a', 'b']))).toBe(false)
    expect(areSetsEqual(new Set(['a', 'b']), new Set(['a']))).toBe(false)
  })

  it('returns false for same-size sets with different members', () => {
    expect(areSetsEqual(new Set(['a', 'b']), new Set(['a', 'c']))).toBe(false)
  })

  it('returns false for an empty set vs a non-empty set', () => {
    expect(areSetsEqual(new Set(), new Set(['a']))).toBe(false)
  })

  it('compares numeric sets by content', () => {
    expect(areSetsEqual(new Set([1, 2, 3]), new Set([3, 2, 1]))).toBe(true)
    expect(areSetsEqual(new Set([1, 2]), new Set([1, 3]))).toBe(false)
  })
})
