import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import { EGOPassiveCard } from '../EGOPassiveCard'
import { IdentityPassiveCard } from '../IdentityPassiveCard'

const slots = { nameSlot: <span>Name</span>, descSlot: <span>Desc</span>, flavorSlot: null }

const slot = (container: HTMLElement, name: string): HTMLElement | null =>
  container.querySelector<HTMLElement>(`[data-slot="${name}"]`)

describe('passive card importance', () => {
  it('tints only the name banner of an assistant level 3 passive in the player palette', () => {
    const { container } = render(
      <IdentityPassiveCard {...slots} isLocked={false} importanceLevel={3} palette="player" />,
    )
    const card = container.firstElementChild as HTMLElement
    const banner = slot(container, 'importance-banner')
    expect(slot(container, 'importance-decoration')?.dataset.importanceVariant).toBe('passive')
    expect(banner?.style.getPropertyValue('--name-body')).toMatch(/^#87cb00(ff)?$/i)
    expect(banner?.style.getPropertyValue('--name-text')).toMatch(/^#f6ff00(ff)?$/i)
    expect(banner?.textContent).toBe('Name')
    expect(card.style.backgroundColor).toBe('')
    expect(card.style.getPropertyValue('--name-body')).toBe('')
    expect(card.style.getPropertyValue('--name-text')).toBe('')
    expect(card.style.paddingBlock).toBe('0.875rem')
    expect(card.style.marginBlock).toBe('-0.875rem')
    expect([undefined, '']).toContain(card.style.paddingInline)
    expect([undefined, '']).toContain(card.style.marginInline)
  })

  it('uses the enemy palette for an enemy passive', () => {
    const { container } = render(
      <EGOPassiveCard {...slots} isLocked={false} importanceLevel={3} palette="enemy" />,
    )
    const banner = slot(container, 'importance-banner')
    expect(banner?.style.getPropertyValue('--name-body')).toMatch(/^#ff0000(ff)?$/i)
    expect(banner?.style.getPropertyValue('--name-text')).toMatch(/^#f8c200(ff)?$/i)
  })

  it.each([
    ['identity', IdentityPassiveCard],
    ['E.G.O', EGOPassiveCard],
  ] as const)('draws a decorated %s passive content above its decoration', (_name, Card) => {
    const { container } = render(
      <Card {...slots} isLocked={false} importanceLevel={3} palette="player" />,
    )
    const card = container.firstElementChild as HTMLElement
    expect(card.classList).toContain('isolate')
    expect(slot(container, 'importance-decoration')?.classList).toContain('-z-10')
  })

  it.each([
    ['identity', IdentityPassiveCard],
    ['E.G.O', EGOPassiveCard],
  ] as const)('leaves a level 0 %s passive undecorated', (_name, Card) => {
    const { container } = render(
      <Card {...slots} isLocked={false} importanceLevel={0} palette="player" />,
    )
    expect(slot(container, 'importance-decoration')).toBeNull()
    expect(slot(container, 'importance-banner')).toBeNull()
    expect(container.querySelector('.isolate')).toBeNull()
  })
})
