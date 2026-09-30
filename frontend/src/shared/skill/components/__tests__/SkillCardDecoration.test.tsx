import { use } from 'react'
import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import enemy1339 from '@static/data/enemy/1339.json'
import { getCoinSpritePath, getImportanceIconPath } from '@/shared/assets'
import { SkillEntrySchema, SkillIdSchema } from '@/shared/gameData'
import { toSkillCardData } from '../../lib/skillLevel'
import { IdentitySkillCard } from '../IdentitySkillCard'
import { SkillCard } from '../SkillCard'

function renderCard(importanceLevel: number, isLocked = false) {
  return render(
    <IdentitySkillCard
      identityId="1339"
      skillId={skillId}
      entry={entry}
      level={entry.levels[0]}
      renderName={() => <span>Name</span>}
      descriptionSlot={<span>Desc</span>}
      importanceLevel={importanceLevel}
      palette="enemy"
      isLocked={isLocked}
    />,
  )
}

function slot(container: HTMLElement, name: string): HTMLElement {
  const element = container.querySelector<HTMLElement>(`[data-slot="${name}"]`)
  if (element === null) throw new Error(`no ${name}`)
  return element
}

function card(container: HTMLElement): HTMLElement {
  const element = container.querySelector<HTMLElement>('[data-skill-id]')
  if (element === null) throw new Error('no card')
  return element
}

const [phase] = enemy1339.units['1339'].phases
const entry = SkillEntrySchema.parse(phase?.skills['133907'])
const skillId = SkillIdSchema.parse('133907')

describe('skill core decoration from enemy 1339 skill 133907', () => {
  it('passes the level coins and the importance level through the core', () => {
    const { container } = render(
      <IdentitySkillCard
        identityId="1339"
        skillId={skillId}
        entry={entry}
        level={entry.levels[0]}
        renderName={() => <span>Name</span>}
        descriptionSlot={<span>Desc</span>}
        importanceLevel={entry.importanceLevel}
        palette="enemy"
      />,
    )
    const green = getCoinSpritePath('GREEN')
    const coinSources = Array.from(container.querySelectorAll('img'))
      .map((img) => img.getAttribute('src'))
      .filter((src) => src === green)
    expect(coinSources).toEqual([green, green])

    expect(entry.importanceLevel).toBe(2)
    expect(
      container.querySelector('[data-slot="importance-icon-image"]')?.getAttribute('src'),
    ).toBe(getImportanceIconPath('enemy', '2'))
    expect(container.querySelector('[data-slot="importance-glow"]')).not.toBeNull()
  })

  it('renders the undecorated layout at importance 0', () => {
    const { container } = render(
      <IdentitySkillCard
        identityId="1339"
        skillId={skillId}
        entry={entry}
        level={entry.levels[0]}
        renderName={() => <span>Name</span>}
        descriptionSlot={<span>Desc</span>}
        importanceLevel={0}
        palette="enemy"
      />,
    )
    expect(container.querySelector('[data-slot="importance-decoration"]')).toBeNull()
    expect(container.firstElementChild?.getAttribute('data-skill-id')).toBe('133907')
    expect(container.firstElementChild?.classList.contains('@container')).toBe(true)
  })

  it('places an identity skill triangle at the unlock position, 86 units in', () => {
    const { container } = renderCard(2)
    expect(
      slot(container, 'importance-decoration').style.getPropertyValue('--importance-icon-right'),
    ).toBe('1.8813rem')
  })
})

describe('skill card frame from enemy 1339 skill 133907', () => {
  it('pads the card 15 units above a 270-unit relative upper block', () => {
    const { container } = renderCard(0)
    expect(card(container).style.paddingTop).toBe('0.3281rem')
    const upper = slot(container, 'skill-upper')
    expect(upper.style.height).toBe('5.9063rem')
    expect(upper.classList.contains('relative')).toBe(true)
  })

  it('places the coin row at (271.19, 14.3)', () => {
    const { container } = renderCard(0)
    const coins = slot(container, 'skill-coins')
    expect(coins.style.left).toBe('5.9323rem')
    expect(coins.style.top).toBe('0.3128rem')
    const row = coins.firstElementChild as HTMLElement
    expect(row.style.getPropertyValue('--coin-width')).toBe('1.4219rem')
    expect(row.style.getPropertyValue('--coin-gap')).toBe('-0.2188rem')
  })

  it('keeps the name area 100 units clear of the right edge', () => {
    const { container } = renderCard(0)
    const area = slot(container, 'skill-name-area')
    expect(area.style.left).toBe('5.985rem')
    expect(area.style.top).toBe('1.6691rem')
    expect(area.style.right).toBe('2.1875rem')
    expect(area.textContent).toBe('Name')
  })

  it('places the level icon and number', () => {
    const { container } = renderCard(0)
    const icon = slot(container, 'skill-level-icon')
    expect(icon.style.left).toBe('6.0747rem')
    expect(icon.style.top).toBe('3.2813rem')
    expect(icon.style.width).toBe('1.666rem')
    expect(icon.style.height).toBe('1.8878rem')
    const level = slot(container, 'skill-level')
    expect(level.style.left).toBe('7.9778rem')
    expect(level.style.top).toBe('4.2rem')
    expect(level.style.transform).toBe('translateY(-50%)')
    expect(level.style.fontSize).toBe('1.6406rem')
    expect(level.style.maxWidth).toBe('1.8327rem')
  })

  it('places the attack-weight label and count', () => {
    const { container } = renderCard(0)
    const label = slot(container, 'skill-target-label')
    expect(label.style.left).toBe('9.9663rem')
    expect(label.style.top).toBe('3.8084rem')
    expect(label.style.width).toBe('3.9602rem')
    expect(label.style.height).toBe('0.8313rem')
    expect(label.style.fontSize).toBe('0.7219rem')
    const count = slot(container, 'skill-target-count')
    expect(count.style.left).toBe('14.0844rem')
    expect(count.style.width).toBe('8.2556rem')
  })

  it('insets the lower block 30 units on each side', () => {
    const { container } = renderCard(0)
    const lower = slot(container, 'skill-lower')
    expect(lower.style.paddingInline).toBe('0.6563rem')
    expect(lower.textContent).toBe('Desc')
  })

  it.each([
    [0, '0.3281rem'],
    [2, '0.9844rem'],
  ])('pads the card bottom at importance %i by %s', (level, paddingBottom) => {
    const { container } = renderCard(level)
    expect(card(container).style.paddingBottom).toBe(paddingBottom)
  })

  it('draws the lock at R 49 / B 83, 250 × 355, alpha .275, below the content', () => {
    const { container } = renderCard(0, true)
    const lock = slot(container, 'skill-lock')
    expect(lock.style.right).toBe('1.0719rem')
    expect(lock.style.bottom).toBe('1.8156rem')
    expect(lock.style.width).toBe('5.4688rem')
    expect(lock.style.height).toBe('7.7656rem')
    expect(lock.style.opacity).toBe('0.275')
    expect(lock.classList.contains('-z-10')).toBe(true)
    expect(lock.classList.contains('object-contain')).toBe(true)
  })

  it('draws the importance decoration below the card content in an isolated wrapper', () => {
    const { container } = renderCard(2)
    const wrapper = card(container).parentElement as HTMLElement
    expect(wrapper.classList.contains('isolate')).toBe(true)
    expect(slot(container, 'importance-decoration').classList.contains('-z-10')).toBe(true)
  })
})

const PENDING = new Promise<never>(() => undefined)

function SuspendedName(): never {
  return use(PENDING)
}

function renderCore(nameSlot: React.ReactNode, sanityCost?: number) {
  const [level] = entry.levels
  return render(
    <SkillCard
      skillId="133907"
      skillData={toSkillCardData(entry, level)}
      coins={level.coins}
      skillImagePath={undefined}
      skillTier={entry.skillTier}
      nameSlot={nameSlot}
      descriptionSlot={<span>Desc</span>}
      importanceLevel={0}
      palette="enemy"
      {...(sanityCost !== undefined && { sanityCost })}
    />,
  )
}

describe('skill card upper block slots', () => {
  it('draws the loading plate with the client plate geometry', () => {
    const { container } = renderCore(<SuspendedName />)
    const area = slot(container, 'skill-name-area')
    const body = area.firstElementChild?.firstElementChild?.firstElementChild as HTMLElement
    expect(body.style.height).toBe('1.5166rem')
    expect(body.style.fontSize).toBe('0.9844rem')
    expect(body.style.paddingLeft).toBe('0.4375rem')
    expect(body.style.paddingRight).toBe('1.0938rem')
    expect(body.style.paddingBottom).toBe('0.2188rem')
  })

  it('puts an E.G.O sanity cost under the attack-weight label', () => {
    const { container } = renderCore(<span>Name</span>, 12)
    const cost = slot(container, 'skill-sanity-cost')
    expect(cost.parentElement?.getAttribute('data-slot')).toBe('skill-upper')
    expect(cost.style.left).toBe('9.9663rem')
    expect(cost.style.top).toBe('4.6397rem')
    expect(cost.style.fontSize).toBe('0.7219rem')
    expect(cost.textContent).toContain('12')
  })

  it('draws no sanity cost without one', () => {
    const { container } = renderCore(<span>Name</span>)
    expect(container.querySelector('[data-slot="skill-sanity-cost"]')).toBeNull()
  })
})
