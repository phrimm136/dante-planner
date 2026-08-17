import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { useProgressiveCount } from '../useProgressiveReveal'

function Grid({ items, step }: { items: number[]; step: number }) {
  // Derived during render, as every list page does: a new array identity each pass.
  const sorted = [...items].sort((a, b) => a - b)
  const displayCount = useProgressiveCount({ total: sorted.length, step, initial: step })
  return <span data-testid="count">{sorted.slice(0, displayCount).length}</span>
}

function flushFrames(times: number) {
  for (let i = 0; i < times; i += 1) {
    act(() => {
      vi.advanceTimersToNextFrame()
    })
  }
}

describe('useProgressiveCount', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('grows to the total even though the caller rebuilds its array every render', () => {
    render(<Grid items={Array.from({ length: 25 }, (_, i) => i)} step={5} />)

    expect(screen.getByTestId('count')).toHaveTextContent('5')

    flushFrames(4)

    expect(screen.getByTestId('count')).toHaveTextContent('25')
  })

  it('never exceeds the total', () => {
    render(<Grid items={Array.from({ length: 7 }, (_, i) => i)} step={5} />)

    flushFrames(5)

    expect(screen.getByTestId('count')).toHaveTextContent('7')
  })

  it('restarts from the first batch when the data set size changes', () => {
    const { rerender } = render(<Grid items={Array.from({ length: 25 }, (_, i) => i)} step={5} />)
    flushFrames(4)
    expect(screen.getByTestId('count')).toHaveTextContent('25')

    rerender(<Grid items={Array.from({ length: 40 }, (_, i) => i)} step={5} />)

    expect(screen.getByTestId('count')).toHaveTextContent('5')
  })
})
