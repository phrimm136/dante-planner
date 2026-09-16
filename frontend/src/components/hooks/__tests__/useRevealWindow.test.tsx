import { describe, expect, it } from 'vitest'
import {
  columnCountFor,
  growWindow,
  isIndexRevealed,
  isWindowComplete,
  startIndexFor,
} from '../useRevealWindow'

describe('isIndexRevealed', () => {
  const window_ = { start: 40, down: 10, up: 10 }

  it('includes both edges of the window', () => {
    expect(isIndexRevealed(window_, 30)).toBe(true)
    expect(isIndexRevealed(window_, 49)).toBe(true)
  })

  it('excludes the indices just outside it', () => {
    expect(isIndexRevealed(window_, 29)).toBe(false)
    expect(isIndexRevealed(window_, 50)).toBe(false)
  })

  it('reveals nothing when the window has no depth', () => {
    expect(isIndexRevealed({ start: 0, down: 0, up: 0 }, 0)).toBe(false)
  })
})

describe('startIndexFor', () => {
  it('lands on the first index of the row the viewport starts on', () => {
    expect(startIndexFor(0, 0, 100, 5)).toBe(0)
    expect(startIndexFor(100, 0, 100, 5)).toBe(5)
    expect(startIndexFor(250, 0, 100, 5)).toBe(10)
  })

  it('holds the row until the next boundary is crossed', () => {
    expect(startIndexFor(99, 0, 100, 5)).toBe(0)
    expect(startIndexFor(199, 0, 100, 5)).toBe(5)
  })

  it('measures from the grid top, not the document top', () => {
    expect(startIndexFor(300, 200, 100, 5)).toBe(5)
    expect(startIndexFor(150, 200, 100, 5)).toBe(0)
  })

  it('treats a negative offset as the top of the grid', () => {
    expect(startIndexFor(-500, 0, 100, 5)).toBe(0)
  })

  it('falls back to the top when the geometry is unmeasurable', () => {
    expect(startIndexFor(500, 0, 0, 5)).toBe(0)
    expect(startIndexFor(500, 0, 100, 0)).toBe(0)
  })
})

describe('growWindow', () => {
  it('grows both edges by the step', () => {
    expect(growWindow({ start: 40, down: 10, up: 10 }, 10, 100)).toEqual({
      start: 40,
      down: 20,
      up: 20,
    })
  })

  it('stops each edge at the list bounds', () => {
    expect(growWindow({ start: 5, down: 90, up: 5 }, 10, 100)).toEqual({
      start: 5,
      down: 95,
      up: 5,
    })
  })
})

describe('isWindowComplete', () => {
  it('is complete only once both ends are covered', () => {
    expect(isWindowComplete({ start: 40, down: 60, up: 40 }, 100)).toBe(true)
    expect(isWindowComplete({ start: 40, down: 60, up: 39 }, 100)).toBe(false)
    expect(isWindowComplete({ start: 40, down: 59, up: 40 }, 100)).toBe(false)
  })
})

describe('columnCountFor', () => {
  it('counts the resolved tracks a browser reports', () => {
    expect(columnCountFor('160px 160px 160px')).toBe(3)
  })

  it('falls back to one column when no track resolves', () => {
    expect(columnCountFor('repeat(auto-fill, 160px)')).toBe(1)
    expect(columnCountFor('none')).toBe(1)
  })
})
