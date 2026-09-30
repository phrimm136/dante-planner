import { use } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'

import krTable from '@static/data/fontAdvances/KR.json'
import { FontAdvanceTableSchema } from '@/shared/cardLayout'
import { NamePlate } from '@/shared/gameText'

import { EGOPassiveCard } from '../EGOPassiveCard'
import { IdentityPassiveCard } from '../IdentityPassiveCard'

const slots = {
  renderName: () => <span>Name</span>,
  descSlot: <span>Desc</span>,
  flavorSlot: null,
}

const KR_TABLE = FontAdvanceTableSchema.parse(krTable)

vi.mock('@/shared/cardLayout', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/cardLayout')>()),
  useFontAdvances: () => KR_TABLE,
}))

const slot = (container: HTMLElement, name: string): HTMLElement => {
  const found = container.querySelector<HTMLElement>(`[data-slot="${name}"]`)
  if (found === null) throw new Error(`no ${name}`)
  return found
}

const pending = new Promise<never>(() => undefined)

function NeverResolves() {
  return use(pending)
}

const cards = [
  ['identity', IdentityPassiveCard],
  ['E.G.O', EGOPassiveCard],
] as const

describe('passive box layout', () => {
  it.each(cards.flatMap(([name, Card]) => [0, 3].map((level) => [name, level, Card] as const)))(
    'floors a %s passive box at level %i to 300 units',
    (_name, level, Card) => {
      const { container } = render(
        <Card {...slots} isLocked={false} importanceLevel={level} palette="player" />,
      )
      expect(slot(container, 'passive-box').style.minHeight).toBe('6.5625rem')
      expect(slot(container, 'passive-box').classList).toContain('relative')
    },
  )

  it.each(cards.flatMap(([name, Card]) => [0, 3].map((level) => [name, level, Card] as const)))(
    'caps the %s plate row at level %i 372 units from the right edge',
    (_name, level, Card) => {
      const { container } = render(
        <Card {...slots} isLocked={false} importanceLevel={level} palette="player" />,
      )
      const row = slot(container, 'passive-box').firstElementChild as HTMLElement
      expect(row.textContent).toBe('Name')
      expect(row.style.marginLeft).toBe('0.6125rem')
      expect(row.style.marginRight).toBe('8.1375rem')
    },
  )

  it('keeps the banner colours on the plate inside the margined row', () => {
    const { container } = render(
      <IdentityPassiveCard
        {...slots}
        renderName={(importance) => (
          <NamePlate kind="passive" name="Name" importance={importance} />
        )}
        isLocked={false}
        importanceLevel={3}
        palette="player"
      />,
    )
    const row = slot(container, 'passive-box').firstElementChild as HTMLElement
    const banner = slot(container, 'importance-banner')
    expect(row.style.marginRight).toBe('8.1375rem')
    expect(row.contains(banner)).toBe(true)
    expect(banner.style.getPropertyValue('--name-body')).not.toBe('')
  })

  it.each(cards)('sizes the %s name skeleton as the passive plate', (_name, Card) => {
    const { container } = render(
      <Card
        {...slots}
        renderName={() => <NeverResolves />}
        isLocked={false}
        importanceLevel={0}
        palette="player"
      />,
    )
    const row = slot(container, 'passive-box').firstElementChild as HTMLElement
    const plate = row.querySelector<HTMLElement>('[style*="height"]')
    expect(plate?.style.height).toBe('1.5912rem')
    expect(plate?.style.fontSize).toBe('0.875rem')
    expect(plate?.style.paddingLeft).toBe('0.6563rem')
    expect(plate?.style.paddingRight).toBe('2.1875rem')
  })

  it.each(cards)('insets the %s info text below the header', (_name, Card) => {
    const { container } = render(
      <Card {...slots} isLocked={false} importanceLevel={0} palette="player" />,
    )
    const info = slot(container, 'passive-info')
    expect(info.textContent).toBe('Desc')
    expect(info.style.marginTop).toBe('0.3312rem')
    expect(info.style.paddingLeft).toBe('0.5906rem')
    expect(info.style.paddingRight).toBe('0.4375rem')
  })

  it.each(cards)('stacks the %s box without space-y gaps', (_name, Card) => {
    const { container } = render(
      <Card {...slots} isLocked={false} importanceLevel={3} palette="player" />,
    )
    expect(container.innerHTML).not.toMatch(/space-y-/)
  })

  it('lays out a WRATH 3 sin requirement row', () => {
    const { container } = render(
      <IdentityPassiveCard
        {...slots}
        condition={{ type: 'RESONATE', values: { WRATH: 3 } }}
        isLocked={false}
        importanceLevel={0}
        palette="player"
      />,
    )
    const row = slot(container, 'passive-sin-row')
    expect(row.style.marginTop).toBe('0.4375rem')
    expect(row.style.marginLeft).toBe('0.4375rem')
    expect(row.style.height).toBe('1.6844rem')
    const item = slot(container, 'passive-sin-item')
    expect(item.style.minWidth).toBe('3.7188rem')
    const [icon, times] = [...item.querySelectorAll('img')]
    expect(icon?.getAttribute('alt')).toBe('WRATH')
    expect(icon?.style.width).toBe('1.5313rem')
    expect(icon?.style.height).toBe('1.5313rem')
    expect(times?.style.width).toBe('0.875rem')
    expect(times?.style.height).toBe('0.875rem')
    const count = item.querySelector('span')
    expect(count?.textContent).toBe('3')
    expect(count?.style.fontSize).toBe('1.5313rem')
    const label = slot(container, 'passive-sin-label')
    expect(label.style.fontSize).toBe('1.0938rem')
    expect(label.style.marginLeft).toBe('0.4375rem')
  })

  it('omits the sin requirement row without a condition', () => {
    const { container } = render(
      <IdentityPassiveCard {...slots} isLocked={false} importanceLevel={0} palette="player" />,
    )
    expect(container.querySelector('[data-slot="passive-sin-row"]')).toBeNull()
  })

  it.each([0, 3])(
    'anchors the lock of a level %i passive to the box corner below the content',
    (level) => {
      const { container } = render(
        <IdentityPassiveCard {...slots} isLocked importanceLevel={level} palette="player" />,
      )
      const lock = slot(container, 'passive-lock')
      expect(lock.parentElement).toBe(slot(container, 'passive-box'))
      expect(lock.style.right).toBe('0.4375rem')
      expect(lock.style.bottom).toBe('0.4375rem')
      expect(lock.style.width).toBe('3.2091rem')
      expect(lock.style.height).toBe('4.2678rem')
      expect(lock.style.opacity).toBe('0.298')
      expect(lock.classList).toContain('-z-10')
      expect(lock.classList).toContain('object-contain')
      expect((container.firstElementChild as HTMLElement).classList).toContain('opacity-50')
    },
  )
})
