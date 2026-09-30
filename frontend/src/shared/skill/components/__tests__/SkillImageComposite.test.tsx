import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'

import { SkillImageComposite } from '../SkillImageComposite'

function renderComposite(skillImagePath: string | undefined, showMissingPlaceholder = false) {
  return render(
    <SkillImageComposite
      skillImagePath={skillImagePath}
      attributeType="SCARLET"
      skillTier={2}
      atkType="PENETRATE"
      basePower={3}
      coinPower={2}
      showMissingPlaceholder={showMissingPlaceholder}
    />,
  )
}

describe('SkillImageComposite', () => {
  it('draws the skill image inside the frame when it has a path', () => {
    const { container } = renderComposite('/images/identity/10310/1031001.webp')
    expect(container.querySelector('img[src="/images/identity/10310/1031001.webp"]')).not.toBeNull()
  })

  it('draws the frame only, with no image and no placeholder, when it has no path', () => {
    const { container } = renderComposite(undefined, true)
    expect(container.querySelector('img.object-cover')).toBeNull()
    expect(screen.queryByText('Missing')).toBeNull()
    expect(container.querySelectorAll('img').length).toBeGreaterThan(0)
  })
})

function slot(container: HTMLElement, name: string): HTMLElement {
  const element = container.querySelector<HTMLElement>(`[data-slot="${name}"]`)
  if (element === null) throw new Error(`no ${name}`)
  return element
}

describe('SkillImageComposite placement in the upper block', () => {
  it('draws the frame and its background at (65.4, 23.26), 252 units square', () => {
    const { container } = renderComposite('/images/identity/10310/1031001.webp')
    for (const name of ['skill-frame', 'skill-frame-bg']) {
      const image = slot(container, name)
      expect(image.classList.contains('absolute')).toBe(true)
      expect(image.style.left).toBe('1.4306rem')
      expect(image.style.top).toBe('0.5088rem')
      expect(image.style.width).toBe('5.5125rem')
      expect(image.style.height).toBe('5.5125rem')
    }
  })

  it('centres the attack icon on (184.4, 218.7) with its backing inside the icon box', () => {
    const { container } = renderComposite(undefined)
    const icon = slot(container, 'attack-icon')
    expect(icon.style.left).toBe('4.0337rem')
    expect(icon.style.top).toBe('4.7841rem')
    expect(icon.style.width).toBe('1.365rem')
    expect(icon.style.height).toBe('1.365rem')
    expect(icon.style.transform).toBe('translate(-50%, -50%)')
    const backing = slot(container, 'attack-backing')
    expect(backing.parentElement).toBe(icon)
    expect(backing.classList.contains('inset-0')).toBe(true)
    expect(backing.classList.contains('w-full')).toBe(true)
    expect(backing.classList.contains('h-full')).toBe(true)
    expect(backing.style.width).toBe('')
    expect(icon.firstElementChild).toBe(backing)
  })

  it('centres the coin-scale value and its backing', () => {
    const { container } = renderComposite(undefined)
    const value = slot(container, 'coin-scale')
    expect(value.textContent).toBe('+2')
    expect(value.style.left).toBe('3.9769rem')
    expect(value.style.top).toBe('1.1134rem')
    expect(value.style.transform).toBe('translate(-50%, -50%)')
    expect(value.style.fontSize).toBe('1.3125rem')
    expect(value.style.letterSpacing).toBe('-0.06em')
    expect(value.classList.contains('font-bold')).toBe(false)
    const backing = slot(container, 'coin-scale-bg')
    expect(backing.style.left).toBe('4.0622rem')
    expect(backing.style.top).toBe('1.1812rem')
    expect(backing.style.width).toBe('2.1368rem')
    expect(backing.style.height).toBe('1.3228rem')
    expect(backing.style.transform).toBe('translate(-50%, -50%)')
  })

  it('right-aligns the base power to x 104 with its digit ink centred on y 142.7', () => {
    const { container } = renderComposite(undefined)
    const power = slot(container, 'base-power')
    expect(power.textContent).toBe('3')
    expect(power.style.left).toBe('0px')
    expect(power.style.width).toBe('2.275rem')
    expect(power.style.textAlign).toBe('right')
    expect(power.style.top).toBe('3.1216rem')
    expect(power.style.transform).toBe('translateY(calc(-50% + -0.1531em))')
    expect(power.style.fontSize).toBe('2.2431rem')
    expect(power.classList.contains('text-[48px]')).toBe(false)
  })

  it('draws no attack icon for a defense skill', () => {
    const { container } = render(
      <SkillImageComposite
        skillImagePath={undefined}
        attributeType="SCARLET"
        skillTier={1}
        atkType="NONE"
        basePower={3}
        coinPower={2}
      />,
    )
    expect(container.querySelector('[data-slot="attack-icon"]')).toBeNull()
    expect(container.querySelector('[data-slot="attack-backing"]')).toBeNull()
  })
})
