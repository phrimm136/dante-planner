import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import { TierLevelSelector } from '../TierLevelSelector'

// The global setup mocks IntersectionObserver as a no-op, which keeps the lazy
// inner component from ever mounting. Override per-suite so observe() fires
// the visibility callback synchronously — the inner mounts on first render.
beforeEach(() => {
  globalThis.IntersectionObserver = class IntersectionObserver {
    private cb: IntersectionObserverCallback
    constructor(cb: IntersectionObserverCallback) {
      this.cb = cb
    }
    observe(target: Element) {
      this.cb(
        [{ isIntersecting: true, target } as IntersectionObserverEntry],
        this as unknown as IntersectionObserver
      )
    }
    disconnect() {}
    unobserve() {}
    takeRecords() {
      return []
    }
    readonly root = null
    readonly rootMargin = ''
    readonly thresholds: ReadonlyArray<number> = []
  } as unknown as typeof IntersectionObserver
})

function openPopover(container: HTMLElement) {
  const target = container.querySelector('div.absolute.inset-0') as HTMLElement
  act(() => {
    fireEvent.mouseEnter(target)
  })
}

describe('TierLevelSelector — EGO mode threadspin buttons', () => {
  it('renders exactly 4 threadspin buttons for a 4-cap EGO', async () => {
    const onConfirm = vi.fn()
    const { container } = render(
      <TierLevelSelector
        mode="ego"
        entityId="20101"
        currentThreadspin={4}
        maxThreadspin={4}
        onConfirm={onConfirm}
      >
        <div data-testid="ego-card">card</div>
      </TierLevelSelector>
    )

    openPopover(container)

    expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
    expect(screen.getByRole('button', { name: /tier 4/i })).toBeDefined()
    expect(screen.queryByRole('button', { name: /tier 5/i })).toBeNull()
  })

  it('renders 5 threadspin buttons when maxThreadspin={5}', async () => {
    const onConfirm = vi.fn()
    const { container } = render(
      <TierLevelSelector
        mode="ego"
        entityId="20999"
        currentThreadspin={5}
        maxThreadspin={5}
        onConfirm={onConfirm}
      >
        <div data-testid="ego-card">card</div>
      </TierLevelSelector>
    )

    openPopover(container)

    expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
    expect(screen.getByRole('button', { name: /tier 4/i })).toBeDefined()
    expect(screen.getByRole('button', { name: /tier 5/i })).toBeDefined()
  })

  it('clicking tier 5 then equip confirms with threadspin 5', async () => {
    const onConfirm = vi.fn()
    const { container } = render(
      <TierLevelSelector
        mode="ego"
        entityId="20999"
        currentThreadspin={5}
        maxThreadspin={5}
        onConfirm={onConfirm}
      >
        <div data-testid="ego-card">card</div>
      </TierLevelSelector>
    )

    openPopover(container)

    fireEvent.click(screen.getByRole('button', { name: /tier 5/i }))
    fireEvent.click(screen.getByRole('button', { name: /equip/i }))

    expect(onConfirm).toHaveBeenCalledWith('20999', { threadspin: 5 })
  })

  it('omits the tier 5 button entirely for a 4-cap EGO', async () => {
    const onConfirm = vi.fn()
    const { container } = render(
      <TierLevelSelector
        mode="ego"
        entityId="20101"
        currentThreadspin={3}
        maxThreadspin={4}
        onConfirm={onConfirm}
      >
        <div data-testid="ego-card">card</div>
      </TierLevelSelector>
    )

    openPopover(container)

    expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
    expect(screen.getByRole('button', { name: /tier 2/i })).toBeDefined()
    expect(screen.getByRole('button', { name: /tier 3/i })).toBeDefined()
    expect(screen.getByRole('button', { name: /tier 4/i })).toBeDefined()
    expect(screen.queryByRole('button', { name: /tier 5/i })).toBeNull()
  })
})
