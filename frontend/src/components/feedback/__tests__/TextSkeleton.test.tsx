/**
 * TextSkeleton.test.tsx
 *
 * The stub draws one bar per line, and each bar's height follows the text size it stands
 * in for while its width follows the named width.
 */

import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'

import { TextSkeleton } from '../TextSkeleton'

function bars(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll('[data-slot="skeleton"]'))
}

describe('TextSkeleton', () => {
  it('draws one bar by default', () => {
    const { container } = render(<TextSkeleton />)

    expect(bars(container)).toHaveLength(1)
  })

  it('draws one bar per line', () => {
    const { container } = render(<TextSkeleton lines={4} />)

    expect(bars(container)).toHaveLength(4)
  })

  it('stacks the bars in a gapped column', () => {
    const { container } = render(<TextSkeleton lines={2} />)

    expect(container.firstElementChild?.className).toContain('flex flex-col gap-1')
  })

  it.each([
    ['xs', 'h-4'],
    ['sm', 'h-5'],
    ['base', 'h-6'],
    ['lg', 'h-7'],
    ['xl', 'h-7'],
    ['2xl', 'h-8'],
  ] as const)('maps size %s to %s', (size, expected) => {
    const { container } = render(<TextSkeleton size={size} />)

    expect(bars(container)[0]?.className).toContain(expected)
  })

  it.each([
    ['xs', 'w-16'],
    ['sm', 'w-24'],
    ['md', 'w-32'],
    ['lg', 'w-48'],
    ['full', 'w-full'],
  ] as const)('maps width %s to %s', (width, expected) => {
    const { container } = render(<TextSkeleton width={width} />)

    expect(bars(container)[0]?.className).toContain(expected)
  })

  it('defaults to the sm line height and md width', () => {
    const { container } = render(<TextSkeleton />)

    const bar = bars(container)[0]?.className ?? ''
    expect(bar).toContain('h-5')
    expect(bar).toContain('w-32')
  })

  it('applies the named width to every line', () => {
    const { container } = render(<TextSkeleton lines={3} width="full" />)

    for (const bar of bars(container)) {
      expect(bar.className).toContain('w-full')
    }
  })
})
