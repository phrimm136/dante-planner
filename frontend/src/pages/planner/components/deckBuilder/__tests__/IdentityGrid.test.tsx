import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'

import { toIdentityEntity } from '@/pages/identity'
import { SeededPlannerStore } from '../../../stores/__tests__/plannerStoreHarness'
import { IdentityGrid } from '../IdentityGrid'
import type { UptieTier } from '../../../types/DeckTypes'
import type { IdentityId } from '@/shared/gameData'

vi.mock('@/pages/identity', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/pages/identity')>()),
  IdentityCard: () => <div data-testid="identity-card" />,
}))

const IDENTITY = toIdentityEntity(
  '10101',
  {
    rank: 3,
    updateDate: 20250101,
    unitKeywordList: [],
    skillKeywordList: [],
    battleKeywordList: [],
    attributeType: ['CRIMSON', 'AZURE', 'SCARLET'],
    atkType: ['SLASH', 'PENETRATE', 'HIT'],
    defenseType: ['GUARD'],
    season: 1,
  },
  'Id 10101',
)

const MAX_LEVEL = 60

beforeEach(() => {
  globalThis.IntersectionObserver = class SynchronousIntersectionObserver implements IntersectionObserver {
    private cb: IntersectionObserverCallback
    constructor(cb: IntersectionObserverCallback) {
      this.cb = cb
    }
    observe(target: Element) {
      this.cb([{ isIntersecting: true, target } as IntersectionObserverEntry], this)
    }
    disconnect() {}
    unobserve() {}
    takeRecords(): IntersectionObserverEntry[] {
      return []
    }
    readonly root = null
    readonly rootMargin = ''
    readonly scrollMargin = ''
    readonly thresholds: ReadonlyArray<number> = []
  }
})

function grid(equippedIdentityTierMap: Record<IdentityId, { uptie: UptieTier; level: number }>) {
  return (
    <SeededPlannerStore>
      <IdentityGrid
        sortedIdentities={[IDENTITY]}
        visibleIds={new Set([IDENTITY.id])}
        equippedIds={new Set(Object.keys(equippedIdentityTierMap))}
        equippedIdentityTierMap={equippedIdentityTierMap}
        onEquip={vi.fn()}
        scrollRef={null}
        isActive
        maxLevel={MAX_LEVEL}
      />
    </SeededPlannerStore>
  )
}

function openPopover(container: HTMLElement) {
  const target = container.querySelector('div.absolute.inset-0') as HTMLElement
  act(() => {
    fireEvent.mouseEnter(target)
  })
}

function selectedUptie(): string | undefined {
  return screen
    .getAllByRole('button', { name: /^Tier \d$/ })
    .find((button) => button.getAttribute('data-selected') === 'true')
    ?.querySelector('img')?.alt
}

describe('IdentityGrid — tier selector seeding', () => {
  it('opens the selector at the equipped uptie and level', () => {
    const { container } = render(grid({ [IDENTITY.id]: { uptie: 2, level: 30 } }))

    openPopover(container)

    expect(selectedUptie()).toBe('Tier 2')
    expect(screen.getByRole('spinbutton')).toHaveValue(30)
  })

  it('follows an equipment change while mounted and keeps the popover open', () => {
    const { container, rerender } = render(grid({ [IDENTITY.id]: { uptie: 4, level: 30 } }))

    openPopover(container)
    rerender(grid({ [IDENTITY.id]: { uptie: 2, level: 30 } }))

    expect(selectedUptie()).toBe('Tier 2')
    expect(screen.getByRole('spinbutton')).toHaveValue(30)
  })

  it('opens an unequipped identity at uptie 4 and the max level', () => {
    const { container } = render(grid({}))

    openPopover(container)

    expect(selectedUptie()).toBe('Tier 4')
    expect(screen.getByRole('spinbutton')).toHaveValue(MAX_LEVEL)
  })
})
