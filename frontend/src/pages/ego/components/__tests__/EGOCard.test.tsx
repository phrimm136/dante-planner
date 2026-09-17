import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import { asEGOId } from '@/test-utils/fixtures'
import {
  EGO_CARD_BADGE_SKEW_ORIGIN,
  EGO_CARD_LAYERS,
  EGO_CARD_ROOT_SCALE,
} from '../../lib/cardLayout'
import type { EGOEntity } from '../../types/EGOTypes'
import { EGOCard } from '../EGOCard'

vi.mock('@/shared/assets', () => ({
  getEGOMaskPath: () => '/mock/mask.webp',
  getEGOImagePath: (id: string) => `/mock/portrait-${id}.webp`,
  getEGOCardFramePath: () => '/mock/frame.webp',
  getEGOHoverRingPath: () => '/mock/hoverRing.webp',
  getEGONameBgPath: (attribute: string) => `/mock/nameBg-${attribute}.webp`,
  getEGORankIconPath: (rank: string) => `/mock/rank-${rank}.webp`,
  getEGOCardGradePath: (grade: string) => `/mock/grade-${grade}.webp`,
  getEGOCardThreadspinPath: (level: number) => `/mock/threadspin-${String(level)}.webp`,
  getEGOIconRingPath: () => '/mock/iconRing.webp',
  getSinnerFacePath: (sinner: string) => `/mock/face-${sinner}.webp`,
}))

/** A face carrying no glyph at all, so every character takes its average advance. */
const FONT_TABLE = {
  face: 'Test SDF',
  source: 'test.json',
  ascender: 1,
  descender: 0.25,
  capLine: 0.7,
  lineHeight: 1.25,
  averageAdvance: 0.5,
  advances: {},
  fallbacks: [],
}

vi.mock('@/shared/cardLayout', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/cardLayout')>()),
  useFontAdvances: () => FONT_TABLE,
}))

vi.mock('../../hooks/useEGOListData', () => ({
  useEGOListI18n: () => ({ '20101': 'Fluid Sac' }),
}))

const EGO_20101 = asEGOId('20101')

const EGO: EGOEntity = {
  id: EGO_20101,
  egoType: 'ZAYIN',
  skillKeywordList: [],
  battleKeywordList: [],
  requirements: {},
  attributeType: ['CRIMSON', 'AZURE'],
  atkType: ['SLASH'],
  updateDate: 20240101,
  season: 1,
  maxThreadspin: 4,
}

function renderCard(node: React.ReactElement) {
  return render(<QueryClientProvider client={new QueryClient()}>{node}</QueryClientProvider>)
}

function layerSources(container: HTMLElement): string[] {
  return [...container.querySelectorAll('img')].map((img) => img.getAttribute('src') ?? '')
}

function root(container: HTMLElement): HTMLElement {
  const element = container.firstElementChild
  if (!(element instanceof HTMLElement)) throw new Error('EGOCard rendered no root')
  return element
}

describe('EGOCard', () => {
  it('draws the game’s layers in order', () => {
    const { container } = renderCard(<EGOCard ego={EGO} />)

    expect(layerSources(container)).toEqual([
      '/mock/mask.webp',
      '/mock/portrait-20101.webp',
      '/mock/frame.webp',
      '/mock/hoverRing.webp',
      '/mock/nameBg-CRIMSON.webp',
      '/mock/threadspin-4.webp',
      '/mock/grade-ZAYIN.webp',
      '/mock/rank-ZAYIN.webp',
      '/mock/iconRing.webp',
      '/mock/face-YiSang.webp',
    ])
  })

  it('sizes the root by the card aspect and the game’s scale', () => {
    const { container } = renderCard(<EGOCard ego={EGO} />)
    const style = root(container).style

    expect(root(container).className).toContain('w-full')
    expect(style.containerType).toBe('inline-size')
    expect(style.transform).toBe(`scale(${String(EGO_CARD_ROOT_SCALE)})`)
  })

  it('places every layer at its rect', () => {
    const { container } = renderCard(<EGOCard ego={EGO} />)
    const [mask, , frame, hoverRing, namePlate] = [...container.querySelectorAll('img')]

    expect(mask?.style.left).toBe(`${String(EGO_CARD_LAYERS.portraitWindow.rect.left)}%`)
    expect(mask?.style.top).toBe(`${String(EGO_CARD_LAYERS.portraitWindow.rect.top)}%`)
    expect(frame?.style.width).toBe(`${String(EGO_CARD_LAYERS.frame.rect.width)}%`)
    expect(hoverRing?.style.left).toBe(`${String(EGO_CARD_LAYERS.hoverRing.rect.left)}%`)
    expect(namePlate?.style.height).toBe(`${String(EGO_CARD_LAYERS.namePlate.rect.height)}%`)
  })

  it('contains the rank word sprite in the type-label rect', () => {
    const { container } = renderCard(<EGOCard ego={EGO} />)
    const label = container.querySelector('img[src="/mock/rank-ZAYIN.webp"]') as HTMLImageElement

    expect(label.style.left).toBe(`${String(EGO_CARD_LAYERS.typeLabel.rect.left)}%`)
    expect(label.style.top).toBe(`${String(EGO_CARD_LAYERS.typeLabel.rect.top)}%`)
    expect(label.style.width).toBe(`${String(EGO_CARD_LAYERS.typeLabel.rect.width)}%`)
    expect(label.style.height).toBe(`${String(EGO_CARD_LAYERS.typeLabel.rect.height)}%`)
    expect(label.style.objectFit).toBe('contain')
  })

  it('swaps the rank word with the EGO grade', () => {
    const { container } = renderCard(<EGOCard ego={{ ...EGO, egoType: 'ALEPH' }} />)

    expect(layerSources(container)).toContain('/mock/rank-ALEPH.webp')
    expect(layerSources(container)).not.toContain('/mock/rank-ZAYIN.webp')
  })

  it('leans each badge’s top toward the card centre', () => {
    const { container } = renderCard(<EGOCard ego={EGO} />)
    const images = [...container.querySelectorAll('img')]
    const tier = images.find((img) => img.src.includes('threadspin-'))
    const grade = images.find((img) => img.src.includes('grade-'))

    expect(tier?.style.transform).toBe('skewY(-22deg)')
    expect(grade?.style.transform).toBe('skewY(22deg)')
  })

  it('pivots the threadspin shear on the rect’s left edge and the grade shear on its centre', () => {
    const { container } = renderCard(<EGOCard ego={EGO} />)
    const images = [...container.querySelectorAll('img')]
    const tier = images.find((img) => img.src.includes('threadspin-'))
    const grade = images.find((img) => img.src.includes('grade-'))

    expect(tier?.style.transformOrigin).toBe(EGO_CARD_BADGE_SKEW_ORIGIN.threadspin)
    expect(grade?.style.transformOrigin).toBe(EGO_CARD_BADGE_SKEW_ORIGIN.grade)
  })

  it('draws both badges from the card sprite family, contained in their rects', () => {
    const { container } = renderCard(<EGOCard ego={EGO} />)
    const tier = container.querySelector('img[src="/mock/threadspin-4.webp"]') as HTMLImageElement
    const grade = container.querySelector('img[src="/mock/grade-ZAYIN.webp"]') as HTMLImageElement

    expect(tier.style.objectFit).toBe('contain')
    expect(tier.style.objectPosition).toBe(EGO_CARD_LAYERS.threadspinBadge.origin)
    expect(tier.style.left).toBe(`${String(EGO_CARD_LAYERS.threadspinBadge.rect.left)}%`)
    expect(tier.style.height).toBe(`${String(EGO_CARD_LAYERS.threadspinBadge.rect.height)}%`)
    expect(grade.style.objectFit).toBe('contain')
    expect(grade.style.objectPosition).toBe(EGO_CARD_LAYERS.gradeBadge.origin)
    expect(grade.style.left).toBe(`${String(EGO_CARD_LAYERS.gradeBadge.rect.left)}%`)
    expect(grade.style.height).toBe(`${String(EGO_CARD_LAYERS.gradeBadge.rect.height)}%`)
  })

  it('picks the threadspin numeral by the EGO’s max threadspin', () => {
    const { container } = renderCard(<EGOCard ego={{ ...EGO, maxThreadspin: 5 }} />)

    expect(layerSources(container)).toContain('/mock/threadspin-5.webp')
  })

  it('stencils the portrait with the mask sprite it also draws', () => {
    const { container } = renderCard(<EGOCard ego={EGO} />)
    const window = screen.getByTestId('ego-portrait-window')

    expect(window.style.maskImage).toBe('url(/mock/mask.webp)')
    expect(window.style.maskSize).toBe('contain')
    expect(window.style.maskPosition).toBe('center')
    expect(window.style.maskRepeat).toBe('no-repeat')
    expect(window.style.left).toBe(`${String(EGO_CARD_LAYERS.portraitWindow.rect.left)}%`)
    expect(layerSources(container)[0]).toBe('/mock/mask.webp')
  })

  it('contains the square round layers in their rects so they stay circles', () => {
    const { container } = renderCard(<EGOCard ego={EGO} />)
    const [mask, , frame, hoverRing] = [...container.querySelectorAll('img')]

    expect(mask?.style.objectFit).toBe('contain')
    expect(frame?.style.objectFit).toBe('contain')
    expect(hoverRing?.style.objectFit).toBe('contain')
  })

  it('draws the name plate and the icon ring at the game preserveAspect fit', () => {
    const { container } = renderCard(<EGOCard ego={EGO} />)
    const plate = container.querySelector(
      'img[src="/mock/nameBg-CRIMSON.webp"]',
    ) as HTMLImageElement
    const iconRing = container.querySelector('img[src="/mock/iconRing.webp"]') as HTMLImageElement

    expect(plate.style.objectFit).toBe('contain')
    expect(plate.style.objectPosition).toBe(EGO_CARD_LAYERS.namePlate.origin)
    expect(iconRing.style.objectFit).toBe('contain')
    expect(iconRing.style.objectPosition).toBe(EGO_CARD_LAYERS.iconRing.origin)
  })

  it('hides the hover ring until hover and holds its tint constant', () => {
    const { container } = renderCard(<EGOCard ego={EGO} />)
    const rings = [...container.querySelectorAll('img[src="/mock/hoverRing.webp"]')]

    expect(rings).toHaveLength(1)
    const [hoverRing] = rings

    expect(hoverRing?.className).toContain('opacity-0')
    expect(hoverRing?.className).toContain('group-hover:opacity-100')
    expect(hoverRing?.className).not.toContain('transition')
    expect(hoverRing?.className).toContain('brightness-[var(--ego-hover-ring-brightness)]')
    expect(hoverRing?.className).not.toContain('group-hover:brightness')
    expect(root(container).style.getPropertyValue('--ego-hover-ring-brightness')).toBe('0.784')
  })

  it('adds the selected ring over the hover ring, at the sprite’s own white', () => {
    const { container } = renderCard(<EGOCard ego={EGO} isSelected />)
    const rings = [...container.querySelectorAll('img[src="/mock/hoverRing.webp"]')]

    expect(rings).toHaveLength(2)
    const [hoverRing, selectedRing] = rings

    expect(hoverRing?.className).toContain('opacity-0')
    expect(hoverRing?.className).toContain('brightness-[var(--ego-hover-ring-brightness)]')
    expect(selectedRing?.className).not.toContain('brightness')
    expect(selectedRing?.className).not.toContain('opacity-0')
    // Both nodes carry the client's one ring rect, so the sprites sit on each other.
    expect(selectedRing?.getAttribute('style')).toBe(hoverRing?.getAttribute('style'))
  })

  it('leaves the name plate out when the EGO has no attribute', () => {
    const { container } = renderCard(<EGOCard ego={{ ...EGO, attributeType: [] }} />)

    expect(layerSources(container)).not.toContain('/mock/nameBg-CRIMSON.webp')
  })

  it('renders the overlay above every layer', () => {
    const { container } = renderCard(<EGOCard ego={EGO} overlay={<span data-testid="overlay" />} />)

    expect(root(container).lastElementChild).toBe(screen.getByTestId('overlay'))
  })

  it('passes the className through to the root', () => {
    const { container } = renderCard(<EGOCard ego={EGO} className="custom-class" />)

    expect(root(container).className).toContain('custom-class')
    expect(root(container).className).toContain('group')
  })
})
