import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import importanceColorCode from '@static/data/color/importanceColorCode.json'
import { getImportanceGlowPath, getImportanceIconPath } from '@/shared/assets'
import { ImportanceBanner } from '../ImportanceBanner'
import { ImportanceDecoration } from '../ImportanceDecoration'

const globalsCss = readFileSync(
  resolve(import.meta.dirname, '../../../../styles/globals.css'),
  'utf8',
)

const slot = (container: HTMLElement, name: string): HTMLElement | null =>
  container.querySelector(`[data-slot="${name}"]`)

const cssBlock = (header: string): string => {
  const start = globalsCss.indexOf(`${header} {`)
  expect(start).toBeGreaterThanOrEqual(0)
  return globalsCss.slice(start, globalsCss.indexOf('\n}', start))
}

const variable = (element: HTMLElement | null, name: string): string =>
  element?.style.getPropertyValue(name) ?? ''

describe('ImportanceDecoration', () => {
  it('renders nothing at level 0', () => {
    const { container } = render(<ImportanceDecoration level={0} palette="enemy" variant="skill" />)
    expect(container).toBeEmptyDOMElement()
  })

  it('flickers the level 1 icon between 0.7 and 1.0 over 3 s and draws no glow', () => {
    const { container } = render(
      <ImportanceDecoration level={1} palette="player" variant="skill" />,
    )
    const root = slot(container, 'importance-decoration')
    const icon = slot(container, 'importance-icon')
    expect(slot(container, 'importance-icon-image')?.getAttribute('src')).toBe(
      getImportanceIconPath('player', '1'),
    )
    expect(icon?.classList.contains('importance-flicker-icon')).toBe(true)
    expect(variable(root, '--importance-flicker-min')).toBe('0.7')
    expect(variable(root, '--importance-flicker-max')).toBe('1')
    expect(variable(root, '--importance-flicker-period')).toBe('3s')
    expect(variable(root, '--importance-icon-alpha')).toBe('0.2')
    expect(slot(container, 'importance-glow')).toBeNull()
  })

  it.each([2, 3])('nests the level %i glow inside the icon and flickers the glow', (level) => {
    const { container } = render(
      <ImportanceDecoration level={level} palette="enemy" variant="skill" />,
    )
    const icon = slot(container, 'importance-icon')
    const glow = slot(container, 'importance-glow')
    expect(glow?.parentElement).toBe(icon)
    expect(glow?.getAttribute('src')).toBe(
      getImportanceGlowPath('enemy', String(level) as '2' | '3'),
    )
    expect(glow?.classList.contains('importance-flicker')).toBe(true)
    expect(icon?.classList.contains('importance-flicker-icon')).toBe(false)
  })

  it('keeps the skill bands inside the box at 52.2 client units', () => {
    const { container } = render(<ImportanceDecoration level={2} palette="enemy" variant="skill" />)
    const root = slot(container, 'importance-decoration')
    expect(variable(root, '--importance-band-height')).toBe('1.1412rem')
    expect(variable(root, '--importance-band-offset')).toBe('0rem')
    expect(root?.classList).not.toContain('@container')
    expect(root?.classList).toContain('m-0')
    expect(cssBlock('.importance-band')).toContain('height: var(--importance-band-height)')
    expect(cssBlock('.importance-band-top')).toContain('top: var(--importance-band-offset)')
    expect(cssBlock('.importance-band-bottom')).toContain('bottom: var(--importance-band-offset)')
  })

  it('blinks the stripe at the same period on every level within the level range', () => {
    const periods = [1, 2, 3].map((level) => {
      const { container } = render(
        <ImportanceDecoration level={level} palette="enemy" variant="skill" />,
      )
      return variable(slot(container, 'importance-decoration'), '--importance-blink-period')
    })
    expect(new Set(periods).size).toBe(1)
    expect(Number.parseFloat(periods[0] ?? '')).toBeCloseTo(Math.PI, 5)
    expect(cssBlock('.importance-stripe')).toMatch(
      /importance-blink var\(--importance-blink-period\)/,
    )
  })

  it.each([
    ['skill', 'skill', '1.8813rem', '0.8094rem', '4.375rem'],
    ['passive', 'passive', '0rem', '0.8094rem', '4.375rem'],
  ] as const)(
    'pins the %s icon from the box top-right corner',
    (_name, variant, right, top, size) => {
      const { container } = render(
        <ImportanceDecoration level={1} palette="enemy" variant={variant} />,
      )
      const root = slot(container, 'importance-decoration')
      expect(variable(root, '--importance-icon-right')).toBe(right)
      expect(variable(root, '--importance-icon-top')).toBe(top)
      expect(variable(root, '--importance-icon-size')).toBe(size)
      const rule = cssBlock('.importance-icon')
      expect(rule).toContain('right: var(--importance-icon-right)')
      expect(rule).toContain('top: var(--importance-icon-top)')
    },
  )

  it.each(['enemy', 'player'] as const)('paints the stripe in the %s palette', (palette) => {
    const { container } = render(
      <ImportanceDecoration level={2} palette={palette} variant="skill" />,
    )
    expect(variable(slot(container, 'importance-decoration'), '--importance-stripe-color')).toBe(
      importanceColorCode[palette]['2'].stripe,
    )
  })

  it('carries no panel color on the decoration box', () => {
    const { container } = render(
      <ImportanceDecoration level={3} palette="player" variant="passive" />,
    )
    const root = slot(container, 'importance-decoration')
    expect(variable(root, '--name-body')).toBe('')
    expect(variable(root, '--importance-panel-glow')).toBe('')
    expect(root?.style.backgroundColor).toBe('')
  })

  it('stretches one caution texture copy over 1304.2 client units and the band height, one copy per 10 s', () => {
    const { container } = render(<ImportanceDecoration level={2} palette="enemy" variant="skill" />)
    const stripes = container.querySelectorAll<HTMLElement>('.importance-stripe')
    expect(stripes).toHaveLength(2)
    for (const stripe of stripes) {
      expect(stripe.style.getPropertyValue('--band-h')).toBe('')
      expect(stripe.style.getPropertyValue('--tile-w')).toBe('28.5294rem')
    }
    expect(variable(slot(container, 'importance-decoration'), '--importance-stripe-scroll')).toBe(
      '10s',
    )
    expect(cssBlock('.importance-stripe')).toContain('mask-size: var(--tile-w) 100%;')
    expect(cssBlock('.importance-stripe')).toContain('mask-repeat: repeat-x;')
  })

  it('draws the same band height for skills and passives, fixed in client units rather than the card width', () => {
    const heights = (['skill', 'passive'] as const).map((variant) => {
      const { container } = render(
        <ImportanceDecoration level={2} palette="enemy" variant={variant} />,
      )
      return variable(slot(container, 'importance-decoration'), '--importance-band-height')
    })
    expect(heights).toEqual(['1.1412rem', '1.1412rem'])
    expect(cssBlock('.importance-band')).not.toContain('container-type')
  })

  it('scrolls the top band opposite the bottom band by one tile width', () => {
    const { container } = render(<ImportanceDecoration level={2} palette="enemy" variant="skill" />)
    const top = slot(container, 'importance-band-top')?.firstElementChild
    const bottom = slot(container, 'importance-band-bottom')?.firstElementChild
    expect(top?.classList.contains('importance-stripe-top')).toBe(true)
    expect(bottom?.classList.contains('importance-stripe-top')).toBe(false)

    expect(cssBlock('.importance-stripe-top')).toContain('animation-direction: reverse, normal')
    expect(cssBlock('.importance-stripe')).toMatch(/importance-scroll [^,]* linear infinite,/)
    const keyframes = cssBlock('@keyframes importance-scroll')
    expect(keyframes).toMatch(/from \{\s*mask-position: 0 0;/)
    expect(keyframes).toMatch(/to \{\s*mask-position: var\(--tile-w\) 0;/)
  })

  it('has no glow fade animation', () => {
    expect(globalsCss).not.toContain('importance-glow-fade')
  })

  it('stops every importance animation under reduced motion and rests at the curve maximum', () => {
    const block = cssBlock('@media (prefers-reduced-motion: reduce)')
    for (const selector of [
      '.importance-flicker-icon',
      '.importance-flicker',
      '.importance-stripe',
      ".importance-banner [data-slot='name-plate']",
    ]) {
      expect(block).toContain(selector)
    }
    expect(block).toContain('animation: none')
    expect(cssBlock('.importance-icon')).toContain(
      'opacity: calc(var(--importance-icon-alpha) * var(--importance-flicker-max))',
    )
    expect(cssBlock('.importance-glow')).toContain('opacity: var(--importance-flicker-max)')
    expect(cssBlock(".importance-banner [data-slot='name-plate']")).toContain(
      'filter: drop-shadow(0 0 var(--importance-glow-radius) var(--importance-glow-max))',
    )
  })

  it('scrolls stripes along the top and bottom of a passive box, inside it', () => {
    const { container } = render(
      <ImportanceDecoration level={3} palette="enemy" variant="passive" />,
    )
    const root = slot(container, 'importance-decoration')
    expect(slot(container, 'importance-band-top')?.firstElementChild?.classList).toContain(
      'importance-stripe-top',
    )
    expect(slot(container, 'importance-band-bottom')).not.toBeNull()
    expect(variable(root, '--importance-band-offset')).toBe('0rem')
    expect(variable(root, '--importance-band-height')).toBe('1.1412rem')
    expect(globalsCss).not.toContain('importance-dash-top')
    const band = cssBlock('.importance-band')
    expect(band).toContain('left: 0;')
    expect(band).toContain('right: 0;')
  })
})

describe('ImportanceBanner', () => {
  it('tints only the name plate with the panel color and glows around it', () => {
    const { container } = render(
      <ImportanceBanner level={3} palette="player" className="row">
        <span>Name</span>
      </ImportanceBanner>,
    )
    const banner = slot(container, 'importance-banner')
    expect(banner?.classList.contains('row')).toBe(true)
    expect(variable(banner, '--name-body')).toMatch(/^#87cb00(ff)?$/i)
    expect(variable(banner, '--importance-panel-glow')).toBe(
      importanceColorCode.player['3'].passiveGlow,
    )
    expect(variable(banner, '--name-text')).toMatch(/^#f6ff00(ff)?$/i)
    expect(banner?.style.backgroundColor).toBe('')
    expect(cssBlock('.importance-banner')).not.toContain('background-color')
    expect(cssBlock(".importance-banner [data-slot='name-plate']")).toContain(
      'animation: importance-plate-glow',
    )
    expect(globalsCss).not.toContain('box-shadow: 0 0 var(--importance-glow-radius)')
    expect(cssBlock('@keyframes importance-plate-glow')).toContain(
      'drop-shadow(0 0 var(--importance-glow-radius) var(--importance-glow-min))',
    )
  })

  it('renders a plain row at level 0', () => {
    const { container } = render(
      <ImportanceBanner level={0} palette="player" className="row">
        <span>Name</span>
      </ImportanceBanner>,
    )
    expect(container.innerHTML).toBe('<div class="row"><span>Name</span></div>')
  })
})
