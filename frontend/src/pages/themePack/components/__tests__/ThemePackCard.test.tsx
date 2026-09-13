import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DUNGEON_IDX } from '@/shared/gameData'
import type { ThemePackEntry } from '../../types/ThemePackTypes'
import { ThemePackCard } from '../ThemePackCard'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'EN' },
    }),
  }
})

vi.mock('../../hooks/useThemePackListData', () => ({
  useThemePackListI18n: () => ({ pack1: { name: 'Test Pack' } }),
}))

vi.mock('@/shared/assets', () => ({
  getThemePackImagePath: (id: string) => `/images/themePack/${id}.webp`,
  getThemePackHoverHighlightPath: () => '/images/UI/card/themePack/legacy/hover.webp',
  getThemePackSelectHighlightPath: () => '/images/UI/card/themePack/legacy/focused.webp',
  getThemePackExtremeHighlightPath: () => '/images/UI/card/themePack/legacy/hover-extreme.webp',
}))

vi.mock('@/components/ui/AutoSizeText', () => ({
  AutoSizeText: ({ text }: { text: string }) => <span>{text}</span>,
}))

vi.mock('@/shared/gameText/components/ColoredText', async (importActual) => ({
  ...(await importActual<typeof import('@/shared/gameText/components/ColoredText')>()),
  parseColorTags: (text: string) => text,
}))

function getImages(container: HTMLElement) {
  return Array.from(container.querySelectorAll('img'))
}

function getSrcs(container: HTMLElement) {
  return getImages(container).map((img) => img.getAttribute('src'))
}

describe('ThemePackCard', () => {
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

  const defaultProps = {
    packId: 'pack1',
    packEntry: normalPackEntry,
  }

  describe('Highlight Layers', () => {
    it('renders no highlight overlays by default', () => {
      const { container } = render(<ThemePackCard {...defaultProps} />)

      const images = getImages(container)
      expect(images).toHaveLength(1)
      expect(images[0]).toHaveAttribute('alt', '')
      expect(screen.getByText('Test Pack')).toBeInTheDocument()
    })

    it('renders hover highlight when enableHoverHighlight is true', () => {
      const { container } = render(<ThemePackCard {...defaultProps} enableHoverHighlight />)

      const srcs = getSrcs(container)
      expect(srcs).toHaveLength(2)
      expect(srcs).toContain('/images/UI/card/themePack/legacy/hover.webp')
    })

    it('renders both select and hover layers when isSelected is true', () => {
      const { container } = render(<ThemePackCard {...defaultProps} isSelected />)

      const srcs = getSrcs(container)
      expect(srcs).toHaveLength(3)
      expect(srcs).toContain('/images/UI/card/themePack/legacy/focused.webp')
      expect(srcs).toContain('/images/UI/card/themePack/legacy/hover.webp')
    })

    it('renders hover layer after select layer for z-order priority', () => {
      const { container } = render(
        <ThemePackCard {...defaultProps} isSelected enableHoverHighlight />,
      )

      const srcs = getSrcs(container)
      expect(srcs).toHaveLength(3)
      expect(srcs[0]).toBe('/images/themePack/pack1.webp')
      expect(srcs[1]).toBe('/images/UI/card/themePack/legacy/focused.webp')
      expect(srcs[2]).toBe('/images/UI/card/themePack/legacy/hover.webp')
    })

    it('uses extreme highlight for both layers when pack is extreme', () => {
      const { container } = render(
        <ThemePackCard
          {...defaultProps}
          packEntry={extremePackEntry}
          isSelected
          enableHoverHighlight
        />,
      )

      const srcs = getSrcs(container)
      expect(srcs).toHaveLength(3)
      expect(srcs[1]).toBe('/images/UI/card/themePack/legacy/hover-extreme.webp')
      expect(srcs[2]).toBe('/images/UI/card/themePack/legacy/hover-extreme.webp')
    })

    it('hover layer starts invisible with CSS hover transition', () => {
      const { container } = render(<ThemePackCard {...defaultProps} enableHoverHighlight />)

      const images = getImages(container)
      const hoverLayer = images[1]
      if (!hoverLayer) throw new Error('expected a hover highlight layer')
      expect(hoverLayer.className).toContain('opacity-0')
      expect(hoverLayer.className).toContain('group-hover:opacity-100')
    })

    it('select layer is always visible when rendered', () => {
      const { container } = render(<ThemePackCard {...defaultProps} isSelected />)

      const images = getImages(container)
      const selectLayer = images[1]
      if (!selectLayer) throw new Error('expected a select highlight layer')
      expect(selectLayer).toHaveAttribute('src', '/images/UI/card/themePack/legacy/focused.webp')
      expect(selectLayer.className).not.toContain('opacity-0')
    })
  })

  describe('Lazy loading', () => {
    it('lazy-loads the theme pack art so off-screen grid cards defer their fetch', () => {
      const { container } = render(<ThemePackCard {...defaultProps} />)

      const art = getImages(container)[0]
      expect(art).toHaveAttribute('src', '/images/themePack/pack1.webp')
      expect(art).toHaveAttribute('loading', 'lazy')
    })
  })
})
