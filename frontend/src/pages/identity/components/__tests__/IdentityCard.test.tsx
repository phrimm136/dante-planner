import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

import { IdentityCard } from '../IdentityCard'
import {
  IDENTITY_CARD_LAYERS,
  IDENTITY_NAME_BLOCK,
  IDENTITY_PORTRAIT_WINDOW,
} from '../../lib/cardLayout'
import { asIdentityId } from '@/test-utils/fixtures'
import type { IdentityEntity } from '../../types/IdentityTypes'

vi.mock('@/shared/assets', () => ({
  getIdentityInfoImagePath: (id: string, uptie: number) =>
    `/mock/portrait/${id}-${String(uptie)}.png`,
  getIdentityImageFallbackPath: (id: string) => `/mock/portrait/${id}-fallback.png`,
  getIdentityMaskPath: () => '/mock/identity-mask.png',
  getUptieFramePath: (rank: number, uptie: number) =>
    `/mock/frame/${String(rank)}-${String(uptie)}.png`,
  getIdentityHoverRingPath: (rank: number, uptie: number) =>
    `/mock/hover-ring/${String(rank)}-${String(uptie)}.png`,
  getSinnerIconRingPath: (rank: number) => `/mock/icon-ring/${String(rank)}.png`,
  getSinnerFacePath: (sinner: string) => `/mock/face/${sinner}.png`,
  getIdentityGradePath: (rank: number) => `/mock/grade/${String(rank)}.png`,
}))

vi.mock('../IdentityName', () => ({
  IdentityName: ({ id }: { id: string }) => <span data-testid="identity-name">{id}</span>,
}))

const identity: IdentityEntity = {
  id: asIdentityId('10101'),
  name: 'Test Identity',
  rank: 3,
  updateDate: 20240101,
  unitKeywordList: [],
  skillKeywordList: [],
  battleKeywordList: [],
  attributeType: [],
  atkType: [],
  defenseType: [],
  season: 1,
}

function renderCard(props: Partial<Parameters<typeof IdentityCard>[0]> = {}) {
  return render(<IdentityCard identity={identity} {...props} />)
}

function pct(value: string) {
  return Number(value.replace('%', ''))
}

describe('IdentityCard root', () => {
  it('fills its parent width at the game aspect and opens a query container', () => {
    const { container } = renderCard()
    const root = container.firstElementChild as HTMLElement

    expect(root.style.position).toBe('relative')
    expect(root.style.width).toBe('100%')
    expect(root.style.containerType).toBe('inline-size')
    expect(root.style.aspectRatio).not.toBe('')
  })

  it('carries no intrinsic pixel size', () => {
    const { container } = renderCard()
    const root = container.firstElementChild as HTMLElement

    expect(root.className).not.toContain('w-40')
    expect(root.className).not.toContain('h-56')
  })

  it('hosts the hover group', () => {
    const { container } = renderCard()

    expect((container.firstElementChild as HTMLElement).className).toContain('group')
  })

  it('never scales or transitions on hover', () => {
    const { container } = renderCard()
    const root = container.firstElementChild as HTMLElement

    expect(container.querySelector('style')).toBeNull()
    expect(root.style.transition).toBe('')
  })
})

describe('IdentityCard layers', () => {
  it('draws the game nodes in the game order', () => {
    const { container } = renderCard()
    const stack = container.querySelector('.absolute.inset-0') as HTMLElement

    const sources = Array.from(stack.children).map((child) =>
      child.tagName === 'IMG'
        ? (child as HTMLImageElement).getAttribute('src')
        : (child.querySelector('img')?.getAttribute('src') ?? child.getAttribute('data-testid')),
    )

    expect(sources).toEqual([
      '/mock/portrait/10101-4.png',
      '/mock/frame/3-4.png',
      '/mock/grade/3.png',
      'identity-name-block',
      '/mock/hover-ring/3-4.png',
      '/mock/icon-ring/3.png',
      '/mock/face/YiSang.png',
    ])
  })

  it('masks the portrait window at the mask rect and clips it', () => {
    const { container } = renderCard()
    const window = container.querySelector(
      '[data-testid="identity-portrait-window"]',
    ) as HTMLElement

    expect(window.style.maskImage).toBe('url(/mock/identity-mask.png)')
    expect(window.style.overflow).toBe('hidden')
    expect(pct(window.style.left)).toBeCloseTo(IDENTITY_PORTRAIT_WINDOW.left, 3)
    expect(pct(window.style.top)).toBeCloseTo(IDENTITY_PORTRAIT_WINDOW.top, 3)
    expect(pct(window.style.width)).toBeCloseTo(IDENTITY_PORTRAIT_WINDOW.width, 3)
    expect(pct(window.style.height)).toBeCloseTo(IDENTITY_PORTRAIT_WINDOW.height, 3)
  })

  it('overhangs the portrait inside the window and contains it', () => {
    const { container } = renderCard()
    const portrait = container.querySelector(
      '[data-testid="identity-portrait-window"] img',
    ) as HTMLImageElement

    expect(portrait.style.objectFit).toBe('contain')
    expect(pct(portrait.style.left)).toBeLessThan(0)
    expect(pct(portrait.style.width)).toBeGreaterThan(100)
  })

  it.each([
    ['frame', '/mock/frame/3-4.png', IDENTITY_CARD_LAYERS.frame.origin],
    ['grade', '/mock/grade/3.png', IDENTITY_CARD_LAYERS.grade.origin],
    ['icon ring', '/mock/icon-ring/3.png', IDENTITY_CARD_LAYERS.iconRing.origin],
    ['face', '/mock/face/YiSang.png', IDENTITY_CARD_LAYERS.face.origin],
  ])('draws the %s at the game preserveAspect fit, never stretched', (_label, src, origin) => {
    const { container } = renderCard()
    const image = container.querySelector(`img[src="${src}"]`) as HTMLImageElement

    expect(image.style.objectFit).toBe('contain')
    expect(image.style.objectPosition).toBe(origin)
  })

  it('draws the hover ring on the frame’s own rect', () => {
    const { container } = renderCard()
    const ring = container.querySelector('img[src="/mock/hover-ring/3-4.png"]') as HTMLImageElement
    const frame = container.querySelector('img[src="/mock/frame/3-4.png"]') as HTMLImageElement

    expect(ring.style.objectFit).toBe('contain')
    expect(ring.style.left).toBe(frame.style.left)
    expect(ring.style.top).toBe(frame.style.top)
    expect(ring.style.width).toBe(frame.style.width)
    expect(ring.style.height).toBe(frame.style.height)
    expect(ring.style.objectPosition).toBe(frame.style.objectPosition)
  })

  it.each([
    ['frame', IDENTITY_CARD_LAYERS.frame.rect, '/mock/frame/3-4.png'],
    ['grade', IDENTITY_CARD_LAYERS.grade.rect, '/mock/grade/3.png'],
    ['icon ring', IDENTITY_CARD_LAYERS.iconRing.rect, '/mock/icon-ring/3.png'],
    ['face', IDENTITY_CARD_LAYERS.face.rect, '/mock/face/YiSang.png'],
  ])('positions the %s at its table rect', (_label, rect, src) => {
    const { container } = renderCard()
    const image = container.querySelector(`img[src="${src}"]`) as HTMLImageElement

    expect(pct(image.style.left)).toBeCloseTo(rect.left, 3)
    expect(pct(image.style.top)).toBeCloseTo(rect.top, 3)
    expect(pct(image.style.width)).toBeCloseTo(rect.width, 3)
    expect(pct(image.style.height)).toBeCloseTo(rect.height, 3)
  })

  it('reveals the hover ring only on hover or press', () => {
    const { container } = renderCard()
    const ring = container.querySelector('img[src="/mock/hover-ring/3-4.png"]') as HTMLImageElement

    expect(ring.className).toContain('opacity-0')
    expect(ring.className).toContain('group-hover:opacity-100')
    expect(ring.className).toContain('group-active:opacity-100')
  })

  it('falls back to the fallback portrait once', () => {
    const { container } = renderCard()
    const portrait = container.querySelector(
      '[data-testid="identity-portrait-window"] img',
    ) as HTMLImageElement

    portrait.dispatchEvent(new Event('error', { bubbles: true }))
    expect(portrait.getAttribute('src')).toContain('10101-fallback.png')
  })
})

describe('IdentityCard text', () => {
  it('stacks the level above the name inside the name block', () => {
    renderCard({ level: 45 })
    const block = screen.getByTestId('identity-name-block')
    const level = screen.getByTestId('identity-level')

    expect(block).toContainElement(level)
    expect(block.firstElementChild).toBe(level)
    expect(level.compareDocumentPosition(screen.getByTestId('identity-name'))).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    )
  })

  it('draws the level right-aligned and unclipped, with no box of its own', () => {
    renderCard({ level: 45 })
    const level = screen.getByTestId('identity-level')

    expect(level.textContent).toBe('Lv. 45')
    expect(level.style.textAlign).toBe('right')
    expect(level.style.overflow).toBe('')
    expect(level.style.position).toBe('')
    expect(level.style.top).toBe('')
  })

  it('anchors the name block to its bottom edge with no height', () => {
    renderCard()
    const block = screen.getByTestId('identity-name-block')

    expect(block).toContainElement(screen.getByTestId('identity-name'))
    expect(pct(block.style.bottom)).toBeCloseTo(100 - IDENTITY_NAME_BLOCK.bottomEdge, 3)
    expect(block.style.height).toBe('')
    expect(block.style.flexDirection).toBe('column')
    expect(block.style.textAlign).toBe('right')
  })
})

describe('IdentityCard props', () => {
  it('dims the card stack but not the overlay when selected', () => {
    const { container } = renderCard({
      isSelected: true,
      overlay: <span data-testid="card-overlay" />,
    })
    const stack = container.querySelector('.absolute.inset-0') as HTMLElement

    expect(stack.className).toContain('brightness-50')
    expect(screen.getByTestId('card-overlay').parentElement).toBe(container.firstElementChild)
  })

  it('multiplies the card stack by the given dim and leaves the overlay at full ink', () => {
    renderCard({ dim: 0.5, overlay: <span data-testid="card-overlay" /> })

    expect(screen.getByTestId('identity-card-graphics')).toHaveStyle({
      filter: 'brightness(0.5)',
    })
    expect(screen.getByTestId('card-overlay')).not.toHaveStyle({ filter: 'brightness(0.5)' })
  })

  it('writes no filter at all when the card is drawn unmultiplied', () => {
    renderCard({ dim: 1 })

    expect(screen.getByTestId('identity-card-graphics').style.filter).toBe('')
  })

  it('draws the overlay after every game layer', () => {
    const { container } = renderCard({ overlay: <span data-testid="card-overlay" /> })
    const root = container.firstElementChild as HTMLElement

    expect(root.lastElementChild).toBe(screen.getByTestId('card-overlay'))
  })

  it('reads the portrait and frame at the given uptie', () => {
    const { container } = renderCard({ uptie: 3 })

    expect(container.querySelector('img[src="/mock/portrait/10101-3.png"]')).not.toBeNull()
    expect(container.querySelector('img[src="/mock/frame/3-3.png"]')).not.toBeNull()
  })

  it('merges a caller className onto the root', () => {
    const { container } = renderCard({ className: 'custom-card' })

    expect((container.firstElementChild as HTMLElement).className).toContain('custom-card')
  })
})
