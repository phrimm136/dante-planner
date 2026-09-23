import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import { EGOPassiveCard } from '../EGOPassiveCard'
import { IdentityPassiveCard } from '../IdentityPassiveCard'

const slots = { nameSlot: <span>Name</span>, descSlot: <span>Desc</span>, flavorSlot: null }

const nameRow = (container: HTMLElement): HTMLElement | null =>
  container.querySelector<HTMLElement>('.importance-name')

describe('passive card importance', () => {
  it('decorates an assistant level 3 passive in the player palette', () => {
    const { container } = render(
      <IdentityPassiveCard {...slots} isLocked={false} importanceLevel={3} palette="player" />,
    )
    const decoration = container.querySelector<HTMLElement>('[data-slot="importance-decoration"]')
    expect(decoration?.dataset.importanceVariant).toBe('passive')
    expect(decoration?.style.getPropertyValue('--importance-panel')).toMatch(/^#87cb00(ff)?$/i)
    expect(nameRow(container)?.style.getPropertyValue('--importance-name')).toMatch(
      /^#f6ff00(ff)?$/i,
    )
    expect(nameRow(container)?.textContent).toBe('Name')
  })

  it('uses the enemy palette for an enemy passive', () => {
    const { container } = render(
      <EGOPassiveCard {...slots} isLocked={false} importanceLevel={3} palette="enemy" />,
    )
    const decoration = container.querySelector<HTMLElement>('[data-slot="importance-decoration"]')
    expect(decoration?.style.getPropertyValue('--importance-panel')).toMatch(/^#ff0000(ff)?$/i)
    expect(nameRow(container)?.style.getPropertyValue('--importance-name')).toMatch(
      /^#f8c200(ff)?$/i,
    )
  })

  it.each([
    ['identity', IdentityPassiveCard],
    ['E.G.O', EGOPassiveCard],
  ] as const)('leaves a level 0 %s passive undecorated', (_name, Card) => {
    const { container } = render(
      <Card {...slots} isLocked={false} importanceLevel={0} palette="player" />,
    )
    expect(container.querySelector('[data-slot="importance-decoration"]')).toBeNull()
    expect(nameRow(container)).toBeNull()
    expect(container.querySelector('.isolate')).toBeNull()
  })
})
