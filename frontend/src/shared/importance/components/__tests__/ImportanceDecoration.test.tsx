import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import importanceColorCode from '@static/data/color/importanceColorCode.json'
import { getImportanceGlowPath, getImportanceIconPath } from '@/shared/assets'
import { ImportanceDecoration } from '../ImportanceDecoration'

const globalsCss = readFileSync(
  resolve(import.meta.dirname, '../../../../styles/globals.css'),
  'utf8',
)

const slot = (container: HTMLElement, name: string): HTMLElement | null =>
  container.querySelector(`[data-slot="${name}"]`)

const cssRule = (selector: string): string => {
  const start = globalsCss.indexOf(`${selector} {`)
  expect(start).toBeGreaterThanOrEqual(0)
  return globalsCss.slice(start, globalsCss.indexOf('}', start))
}

describe('ImportanceDecoration', () => {
  it('renders nothing at level 0', () => {
    const { container } = render(<ImportanceDecoration level={0} palette="enemy" variant="skill" />)
    expect(container).toBeEmptyDOMElement()
  })

  it('draws the enemy level 3 icon, glow sprite and both stripe bands', () => {
    const { container } = render(<ImportanceDecoration level={3} palette="enemy" variant="skill" />)
    expect(slot(container, 'importance-icon')?.getAttribute('src')).toBe(
      getImportanceIconPath('enemy', '3'),
    )
    expect(slot(container, 'importance-glow')?.querySelector('img')?.getAttribute('src')).toBe(
      getImportanceGlowPath('enemy', '3'),
    )
    expect(slot(container, 'importance-band-top')).not.toBeNull()
    expect(slot(container, 'importance-band-bottom')).not.toBeNull()
  })

  it('draws the level 1 icon without a glow sprite', () => {
    const { container } = render(
      <ImportanceDecoration level={1} palette="player" variant="skill" />,
    )
    expect(slot(container, 'importance-icon')?.getAttribute('src')).toBe(
      getImportanceIconPath('player', '1'),
    )
    expect(slot(container, 'importance-glow')).toBeNull()
  })

  it.each(['enemy', 'player'] as const)('paints the stripe in the %s palette', (palette) => {
    const { container } = render(
      <ImportanceDecoration level={2} palette={palette} variant="skill" />,
    )
    const root = slot(container, 'importance-decoration')
    expect(root?.style.getPropertyValue('--importance-stripe-color')).toBe(
      importanceColorCode[palette]['2'].stripe,
    )
  })

  it('carries the player level 3 passive panel and glow colors', () => {
    const { container } = render(
      <ImportanceDecoration level={3} palette="player" variant="passive" />,
    )
    const root = slot(container, 'importance-decoration')
    expect(slot(container, 'importance-panel')).not.toBeNull()
    expect(root?.style.getPropertyValue('--importance-panel')).toMatch(/^#87cb00(ff)?$/i)
    expect(root?.style.getPropertyValue('--importance-panel-glow')).toBe(
      importanceColorCode.player['3'].passiveGlow,
    )
  })

  it('draws no panel on a skill', () => {
    const { container } = render(<ImportanceDecoration level={3} palette="enemy" variant="skill" />)
    expect(slot(container, 'importance-panel')).toBeNull()
  })

  it('sizes the stripe tile from the band height by the texture ratio', () => {
    const { container } = render(<ImportanceDecoration level={2} palette="enemy" variant="skill" />)
    const stripes = container.querySelectorAll<HTMLElement>('.importance-stripe')
    expect(stripes).toHaveLength(2)
    for (const stripe of stripes) {
      expect(stripe.style.getPropertyValue('--band-h')).toBe('100cqh')
      expect(stripe.style.getPropertyValue('--tile-w')).toBe('calc(var(--band-h) * 1102 / 42)')
    }
  })

  it('reverses the top band and scrolls both by one tile width without alternating', () => {
    const { container } = render(<ImportanceDecoration level={2} palette="enemy" variant="skill" />)
    const top = slot(container, 'importance-band-top')?.firstElementChild
    const bottom = slot(container, 'importance-band-bottom')?.firstElementChild
    expect(top?.classList.contains('importance-stripe-top')).toBe(true)
    expect(bottom?.classList.contains('importance-stripe-top')).toBe(false)

    expect(cssRule('.importance-stripe-top')).toContain('animation-direction: reverse')
    expect(cssRule('.importance-stripe')).toMatch(/importance-scroll [^,]* linear infinite,/)
    const keyframes = globalsCss.slice(globalsCss.indexOf('@keyframes importance-scroll'))
    expect(keyframes).toMatch(/from \{\s*mask-position: 0 0;/)
    expect(keyframes).toMatch(/to \{\s*mask-position: var\(--tile-w\) 0;/)
  })

  it('stops every importance animation under reduced motion', () => {
    const start = globalsCss.indexOf('@media (prefers-reduced-motion: reduce)')
    expect(start).toBeGreaterThanOrEqual(0)
    const block = globalsCss.slice(start, globalsCss.indexOf('}\n}', start))
    for (const selector of [
      '.importance-flicker',
      '.importance-glow-fade',
      '.importance-stripe',
      '.importance-panel',
    ]) {
      expect(block).toContain(selector)
    }
    expect(block).toContain('animation: none')
  })
})
