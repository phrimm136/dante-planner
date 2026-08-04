import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MAX_LEVEL } from '@/shared/gameData'
import { render, screen, fireEvent, act } from '@testing-library/react'
import { EgoThreadspinSelector, IdentityTierSelector } from '../EntityTierSelectors'

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
        this as unknown as IntersectionObserver,
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

describe('EgoThreadspinSelector — threadspin buttons', () => {
  it('renders exactly 4 threadspin buttons for a 4-cap EGO', async () => {
    const onConfirm = vi.fn()
    const { container } = render(
      <EgoThreadspinSelector
        entityId="20101"
        currentThreadspin={4}
        maxThreadspin={4}
        onConfirm={onConfirm}
      >
        <div data-testid="ego-card">card</div>
      </EgoThreadspinSelector>,
    )

    openPopover(container)

    expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
    expect(screen.getByRole('button', { name: /tier 4/i })).toBeDefined()
    expect(screen.queryByRole('button', { name: /tier 5/i })).toBeNull()
  })

  it('renders 5 threadspin buttons when maxThreadspin={5}', async () => {
    const onConfirm = vi.fn()
    const { container } = render(
      <EgoThreadspinSelector
        entityId="20999"
        currentThreadspin={5}
        maxThreadspin={5}
        onConfirm={onConfirm}
      >
        <div data-testid="ego-card">card</div>
      </EgoThreadspinSelector>,
    )

    openPopover(container)

    expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
    expect(screen.getByRole('button', { name: /tier 4/i })).toBeDefined()
    expect(screen.getByRole('button', { name: /tier 5/i })).toBeDefined()
  })

  it('clicking tier 5 then equip confirms with threadspin 5', async () => {
    const onConfirm = vi.fn()
    const { container } = render(
      <EgoThreadspinSelector
        entityId="20999"
        currentThreadspin={5}
        maxThreadspin={5}
        onConfirm={onConfirm}
      >
        <div data-testid="ego-card">card</div>
      </EgoThreadspinSelector>,
    )

    openPopover(container)

    fireEvent.click(screen.getByRole('button', { name: /tier 5/i }))
    fireEvent.click(screen.getByRole('button', { name: /equip/i }))

    expect(onConfirm).toHaveBeenCalledWith('20999', { threadspin: 5 })
  })

  it('omits the tier 5 button entirely for a 4-cap EGO', async () => {
    const onConfirm = vi.fn()
    const { container } = render(
      <EgoThreadspinSelector
        entityId="20101"
        currentThreadspin={3}
        maxThreadspin={4}
        onConfirm={onConfirm}
      >
        <div data-testid="ego-card">card</div>
      </EgoThreadspinSelector>,
    )

    openPopover(container)

    expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
    expect(screen.getByRole('button', { name: /tier 2/i })).toBeDefined()
    expect(screen.getByRole('button', { name: /tier 3/i })).toBeDefined()
    expect(screen.getByRole('button', { name: /tier 4/i })).toBeDefined()
    expect(screen.queryByRole('button', { name: /tier 5/i })).toBeNull()
  })
})

describe('IdentityTierSelector — uptie and level', () => {
  it('renders the four uptie tiers and no fifth', () => {
    const { container } = render(
      <IdentityTierSelector entityId="10101" currentUptie={4} currentLevel={45} onConfirm={vi.fn()}>
        <div data-testid="identity-card">card</div>
      </IdentityTierSelector>,
    )

    openPopover(container)

    expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
    expect(screen.getByRole('button', { name: /tier 4/i })).toBeDefined()
    expect(screen.queryByRole('button', { name: /tier 5/i })).toBeNull()
  })

  it('confirms with the chosen uptie and level', () => {
    const onConfirm = vi.fn()
    const { container } = render(
      <IdentityTierSelector
        entityId="10101"
        currentUptie={4}
        currentLevel={45}
        onConfirm={onConfirm}
      >
        <div data-testid="identity-card">card</div>
      </IdentityTierSelector>,
    )

    openPopover(container)

    fireEvent.click(screen.getByRole('button', { name: /tier 2/i }))
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '30' } })
    fireEvent.click(screen.getByRole('button', { name: /equip/i }))

    expect(onConfirm).toHaveBeenCalledWith('10101', { uptie: 2, level: 30 })
  })

  it('clamps a level above the cap', () => {
    const onConfirm = vi.fn()
    const { container } = render(
      <IdentityTierSelector
        entityId="10101"
        currentUptie={4}
        currentLevel={45}
        onConfirm={onConfirm}
      >
        <div data-testid="identity-card">card</div>
      </IdentityTierSelector>,
    )

    openPopover(container)

    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '9999' } })
    fireEvent.click(screen.getByRole('button', { name: /equip/i }))

    expect(onConfirm).toHaveBeenCalledWith('10101', { uptie: 4, level: MAX_LEVEL })
  })

  it('offers no unequip button', () => {
    const { container } = render(
      <IdentityTierSelector entityId="10102" currentUptie={4} currentLevel={45} onConfirm={vi.fn()}>
        <div data-testid="identity-card">card</div>
      </IdentityTierSelector>,
    )

    openPopover(container)

    expect(screen.queryByRole('button', { name: /unequip/i })).toBeNull()
  })
})

describe('EgoThreadspinSelector — unequip', () => {
  it('offers unequip for an equipped non-base EGO at its current threadspin', () => {
    const onUnequip = vi.fn()
    const { container } = render(
      <EgoThreadspinSelector
        entityId="20102"
        currentThreadspin={4}
        maxThreadspin={4}
        isSelected
        onConfirm={vi.fn()}
        onUnequip={onUnequip}
      >
        <div data-testid="ego-card">card</div>
      </EgoThreadspinSelector>,
    )

    openPopover(container)

    fireEvent.click(screen.getByRole('button', { name: /unequip/i }))

    expect(onUnequip).toHaveBeenCalledWith('20102')
  })

  it('never offers unequip for a base EGO', () => {
    const { container } = render(
      <EgoThreadspinSelector
        entityId="20101"
        currentThreadspin={4}
        maxThreadspin={4}
        isSelected
        onConfirm={vi.fn()}
        onUnequip={vi.fn()}
      >
        <div data-testid="ego-card">card</div>
      </EgoThreadspinSelector>,
    )

    openPopover(container)

    expect(screen.queryByRole('button', { name: /unequip/i })).toBeNull()
    expect(screen.getByRole('button', { name: /equip/i })).toBeDefined()
  })

  it('switches back to equip once a different threadspin is picked', () => {
    const { container } = render(
      <EgoThreadspinSelector
        entityId="20102"
        currentThreadspin={4}
        maxThreadspin={4}
        isSelected
        onConfirm={vi.fn()}
        onUnequip={vi.fn()}
      >
        <div data-testid="ego-card">card</div>
      </EgoThreadspinSelector>,
    )

    openPopover(container)

    fireEvent.click(screen.getByRole('button', { name: /tier 2/i }))

    expect(screen.queryByRole('button', { name: /unequip/i })).toBeNull()
    expect(screen.getByRole('button', { name: /equip/i })).toBeDefined()
  })
})
