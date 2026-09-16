import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DUNGEON_IDX } from '@/shared/gameData'
import type { PctRect } from '@/shared/cardLayout'
import type { ThemePackEntry } from '../../types/ThemePackTypes'
import { THEME_PACK_HOVER_FADE_MS, THEME_PACK_LAYOUT } from '../../lib/cardLayout'
import { ThemePackCard } from '../ThemePackCard'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'EN' } }),
}))

vi.mock('../../hooks/useThemePackListData', () => ({
  useThemePackListI18n: () => ({ pack1: { name: 'Test Pack' } }),
}))

vi.mock('@/shared/assets', () => ({
  getThemePackImagePath: (id: string) => `/images/themePack/${id}.webp`,
  getThemePackHoverPath: () => '/hover.webp',
  getThemePackFocusedPath: () => '/focused.webp',
  getThemePackHoverExtremePath: () => '/hover-extreme.webp',
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

const CARD_WIDTH = 310

const RECT_PRECISION = 1e4

function round(value: number) {
  return Math.round(value * RECT_PRECISION) / RECT_PRECISION
}

function images(container: HTMLElement) {
  return Array.from(container.querySelectorAll('img'))
}

function srcs(container: HTMLElement) {
  return images(container).map((img) => img.getAttribute('src'))
}

/** The rect an element carries, read back from its inline style. */
function rectOf(element: HTMLElement): PctRect {
  const { left, top, width, height } = element.style
  return {
    left: round(Number.parseFloat(left)),
    top: round(Number.parseFloat(top)),
    width: round(Number.parseFloat(width)),
    height: round(Number.parseFloat(height)),
  }
}

/** The same rect, rounded the way `rectOf` rounds what it reads back. */
function asRendered(rect: PctRect): PctRect {
  return {
    left: round(rect.left),
    top: round(rect.top),
    width: round(rect.width),
    height: round(rect.height),
  }
}

const normalPackEntry: ThemePackEntry = {
  themePackConfig: { textColor: 'FFFFFF' },
  exceptionConditions: [{ dungeonIdx: DUNGEON_IDX.HARD }],
  specificEgoGiftPool: [],
}

const extremePackEntry: ThemePackEntry = {
  themePackConfig: { textColor: 'FF0000' },
  exceptionConditions: [{ dungeonIdx: DUNGEON_IDX.EXTREME }],
  specificEgoGiftPool: [],
}

const defaultProps = { packId: 'pack1', packEntry: normalPackEntry, width: CARD_WIDTH }

describe('ThemePackCard', () => {
  describe('root', () => {
    it('is a container box at the composed art aspect, sized by its consumer', () => {
      const { container } = render(<ThemePackCard {...defaultProps} />)

      const root = container.firstElementChild as HTMLElement
      expect(root.className).toContain('w-full')
      expect(root.className).toContain('group')
      expect(root.style.containerType).toBe('inline-size')
      expect(Number.parseFloat(root.style.aspectRatio)).toBeCloseTo(240 / 395, 6)
    })
  })

  describe('composed art', () => {
    it('fills the root and is stretched to it', () => {
      const { container } = render(<ThemePackCard {...defaultProps} />)

      const art = images(container)[0]
      if (!art) throw new Error('expected the composed pack art')
      expect(art).toHaveAttribute('src', '/images/themePack/pack1.webp')
      expect(art.style.objectFit).toBe('fill')
      expect(rectOf(art)).toEqual({ left: 0, top: 0, width: 100, height: 100 })
    })

    it('lazy-loads so off-screen grid cards defer their fetch', () => {
      const { container } = render(<ThemePackCard {...defaultProps} />)

      expect(images(container)[0]).toHaveAttribute('loading', 'lazy')
    })
  })

  describe('highlight sprites', () => {
    it('draws none by default', () => {
      const { container } = render(<ThemePackCard {...defaultProps} />)

      expect(srcs(container)).toEqual(['/images/themePack/pack1.webp'])
    })

    it('draws the hover sprite only when hover highlight is enabled', () => {
      const { container } = render(<ThemePackCard {...defaultProps} enableHoverHighlight />)

      expect(srcs(container)).toEqual(['/images/themePack/pack1.webp', '/hover.webp'])
    })

    it('draws the focused sprite only when the card is selected', () => {
      const { container } = render(<ThemePackCard {...defaultProps} isSelected />)

      expect(srcs(container)).toEqual(['/images/themePack/pack1.webp', '/focused.webp'])
    })

    it('stacks art, then hover, then focused', () => {
      const { container } = render(
        <ThemePackCard {...defaultProps} enableHoverHighlight isSelected />,
      )

      expect(srcs(container)).toEqual([
        '/images/themePack/pack1.webp',
        '/hover.webp',
        '/focused.webp',
      ])
    })

    it('serves both states from the one extreme sprite on an extreme pack', () => {
      const { container } = render(
        <ThemePackCard
          {...defaultProps}
          packEntry={extremePackEntry}
          enableHoverHighlight
          isSelected
        />,
      )

      expect(srcs(container)).toEqual([
        '/images/themePack/pack1.webp',
        '/hover-extreme.webp',
        '/hover-extreme.webp',
      ])
    })

    it('places the sprites at the normal overlay rect', () => {
      const { container } = render(
        <ThemePackCard {...defaultProps} enableHoverHighlight isSelected />,
      )

      const [, hover, focused] = images(container)
      if (!hover || !focused) throw new Error('expected both highlight sprites')
      expect(rectOf(hover)).toEqual(asRendered(THEME_PACK_LAYOUT.normal.overlay.rect))
      expect(rectOf(focused)).toEqual(asRendered(THEME_PACK_LAYOUT.normal.overlay.rect))
    })

    it('contains the sprites in their rect, the way the game preserves their aspect', () => {
      const { container } = render(
        <ThemePackCard {...defaultProps} enableHoverHighlight isSelected />,
      )

      const [, hover, focused] = images(container)
      if (!hover || !focused) throw new Error('expected both highlight sprites')
      expect(hover.style.objectFit).toBe('contain')
      expect(focused.style.objectFit).toBe('contain')
      expect(hover.style.objectFit).not.toBe('fill')
      expect(focused.style.objectFit).not.toBe('fill')
    })

    it('places the sprites at the extreme overlay rect on an extreme pack', () => {
      const { container } = render(
        <ThemePackCard
          {...defaultProps}
          packEntry={extremePackEntry}
          enableHoverHighlight
          isSelected
        />,
      )

      const [, hover, focused] = images(container)
      if (!hover || !focused) throw new Error('expected both highlight sprites')
      expect(rectOf(hover)).toEqual(asRendered(THEME_PACK_LAYOUT.extreme.overlay.rect))
      expect(rectOf(focused)).toEqual(asRendered(THEME_PACK_LAYOUT.extreme.overlay.rect))
    })

    it('fades the hover sprite in on hover and on touch press', () => {
      const { container } = render(<ThemePackCard {...defaultProps} enableHoverHighlight />)

      const hover = images(container)[1]
      if (!hover) throw new Error('expected the hover sprite')
      expect(hover.className).toContain('opacity-0')
      expect(hover.className).toContain('group-hover:opacity-100')
      expect(hover.className).toContain('group-active:opacity-100')
      expect(hover.className).toContain('transition-opacity')
      expect(hover.style.transitionDuration).toBe(`${String(THEME_PACK_HOVER_FADE_MS)}ms`)
    })

    it('holds the focused sprite on with no transition', () => {
      const { container } = render(<ThemePackCard {...defaultProps} isSelected />)

      const focused = images(container)[1]
      if (!focused) throw new Error('expected the focused sprite')
      expect(focused.className).not.toContain('opacity-0')
      expect(focused.style.transitionDuration).toBe('')
    })
  })

  describe('name box', () => {
    it('prints the localized name at the normal name rect', () => {
      render(<ThemePackCard {...defaultProps} />)

      const box = screen.getByText('Test Pack').parentElement
      if (!box) throw new Error('expected a name box')
      expect(rectOf(box)).toEqual(asRendered(THEME_PACK_LAYOUT.normal.name))
      expect(box.className).toContain('pointer-events-none')
    })

    it('uses the extreme name rect on an extreme pack', () => {
      render(<ThemePackCard {...defaultProps} packEntry={extremePackEntry} />)

      const box = screen.getByText('Test Pack').parentElement
      if (!box) throw new Error('expected a name box')
      expect(rectOf(box)).toEqual(asRendered(THEME_PACK_LAYOUT.extreme.name))
    })
  })

  describe('overlay', () => {
    it('draws consumer overlay content above every card layer', () => {
      const { container } = render(
        <ThemePackCard {...defaultProps} overlay={<span data-testid="badge" />} />,
      )

      const root = container.firstElementChild
      if (!root) throw new Error('expected a card root')
      expect(root.lastElementChild).toBe(screen.getByTestId('badge'))
    })
  })
})
