import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import { PassiveIdSchema } from '@/shared/gameData'
import { EGOPassiveCard } from '../EGOPassiveCard'
import { PassiveRun, PassiveTab } from '../EGOPassivesPane'
import { IdentityPassiveCard } from '../IdentityPassiveCard'
import { IdentityPassivesPane } from '../IdentityPassivesPane'

const globalsCss = readFileSync(
  resolve(import.meta.dirname, '../../../../styles/globals.css'),
  'utf8',
)
const paneRules = globalsCss.match(/\.importance-pane[^{]*\{[^}]*\}/g) ?? []

const slots = {
  renderName: () => <span>Name</span>,
  descSlot: <span>Desc</span>,
  flavorSlot: null,
}

let sheet: HTMLStyleElement
beforeAll(() => {
  sheet = document.createElement('style')
  sheet.textContent = paneRules.join('\n')
  document.head.append(sheet)
})
afterAll(() => sheet.remove())

function renderPane(levels: readonly number[], wrapped: boolean) {
  const passives = levels.map((_, index) => ({ id: String(index), isLocked: index === 1 }))
  const { container } = render(
    <PassiveTab>
      <PassiveRun
        passives={passives}
        renderPassive={(id, isLocked) => {
          const passive = (
            <EGOPassiveCard
              {...slots}
              isLocked={isLocked}
              importanceLevel={levels[Number(id)] ?? 0}
              palette="enemy"
            />
          )
          return wrapped ? <div data-passive-id={id}>{passive}</div> : passive
        }}
      />
    </PassiveTab>,
  )
  return container
}

function boxes(container: HTMLElement): HTMLElement[] {
  return [...container.querySelectorAll<HTMLElement>('[data-slot="passive-box"]')].map(
    (box) => box.parentElement as HTMLElement,
  )
}

const margins = (box: HTMLElement) => {
  const style = getComputedStyle(box)
  return [style.marginTop, style.marginBottom]
}

describe('importance bands at the passive pane edges', () => {
  it('ships a first-child and a last-child rule for the pane', () => {
    expect(paneRules).toHaveLength(2)
  })

  it.each([
    ['inside a per-item wrapper', true],
    ['as direct pane children', false],
  ])('cancels only the outer overhang of decorated edge passives %s', (_name, wrapped) => {
    const [first, middle, last] = boxes(renderPane([3, 3, 3], wrapped))
    for (const box of [first, middle, last]) {
      expect(box?.hasAttribute('data-importance-box')).toBe(true)
      expect(box?.style.marginBlock).toBe('-0.875rem')
    }
    expect(margins(first as HTMLElement)[0]).toBe('0px')
    expect(margins(first as HTMLElement)[1]).not.toBe('0px')
    expect(margins(middle as HTMLElement)).not.toContain('0px')
    expect(margins(last as HTMLElement)[0]).not.toBe('0px')
    expect(margins(last as HTMLElement)[1]).toBe('0px')
  })

  it('cancels both overhangs of a lone decorated passive', () => {
    const [only] = boxes(renderPane([2], true))
    expect(margins(only as HTMLElement)).toEqual(['0px', '0px'])
  })

  it('leaves undecorated edge passives in place and pads the pane 15 units at both ends', () => {
    const container = renderPane([0, 3, 0], true)
    const [first, middle, last] = boxes(container)
    expect(first?.hasAttribute('data-importance-box')).toBe(false)
    expect(last?.hasAttribute('data-importance-box')).toBe(false)
    expect(margins(first as HTMLElement)).not.toContain('0px')
    expect(margins(last as HTMLElement)).not.toContain('0px')
    expect(middle?.style.marginBlock).toBe('-0.875rem')
    expect(margins(middle as HTMLElement)).not.toContain('0px')

    const pane = container.firstElementChild as HTMLElement
    expect(pane.classList).toContain('importance-pane')
    expect(pane.style.paddingTop).toBe('0.3281rem')
    expect(pane.style.paddingBottom).toBe('0.3281rem')
  })

  it('keeps the overhang of the first identity passive under its section header', () => {
    const { container } = render(
      <IdentityPassivesPane
        passives={{
          battlePassiveList: [[PassiveIdSchema.parse('1010101')]],
          supportPassiveList: [[PassiveIdSchema.parse('1010121')]],
        }}
        uptieLevel={1}
        renderPassive={(id, isLocked) => (
          <IdentityPassiveCard
            {...slots}
            key={id}
            isLocked={isLocked}
            importanceLevel={3}
            palette="enemy"
          />
        )}
      />,
    )
    const [battle, support] = boxes(container)
    expect(margins(battle as HTMLElement)).not.toContain('0px')
    expect(margins(support as HTMLElement)[0]).not.toBe('0px')
    expect(margins(support as HTMLElement)[1]).toBe('0px')
  })
})
