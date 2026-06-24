import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
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

vi.mock('@/lib/assetPaths', () => ({
  getThemePackImagePath: (id: string) => `/images/themePack/${id}.webp`,
  getThemePackHoverHighlightPath: () => '/images/UI/themePack/onHover.webp',
  getThemePackSelectHighlightPath: () => '/images/UI/themePack/onSelect.webp',
  getThemePackExtremeHighlightPath: () => '/images/UI/themePack/extremeHighlight.webp',
}))

vi.mock('@/components/common/AutoSizeText', () => ({
  AutoSizeText: ({ text }: { text: string }) => <span>{text}</span>,
}))

vi.mock('@/components/common/ColoredText', async (importActual) => ({
  ...(await importActual<typeof import('@/components/common/ColoredText')>()),
  parseColorTags: (text: string) => text,
}))

function getImages(container: HTMLElement) {
  return Array.from(container.querySelectorAll('img'))
}

function getSrcs(container: HTMLElement) {
  return getImages(container).map((img) => img.getAttribute('src'))
}

describe('ThemePackCard', () => {
  const normalPackEntry = {
    floorNumber: 1,
    themePackConfig: { textColor: 'FFFFFF' },
    exceptionConditions: [{ dungeonIdx: 1 }],
  }

  const extremePackEntry = {
    floorNumber: 1,
    themePackConfig: { textColor: 'FF0000' },
    exceptionConditions: [{ dungeonIdx: 3 }],
  }

  const defaultProps = {
    packId: 'pack1',
    packEntry: normalPackEntry,
    packName: 'Test Pack',
  }

  describe('Highlight Layers', () => {
    it('renders no highlight overlays by default', () => {
      const { container } = render(<ThemePackCard {...defaultProps} />)

      const images = getImages(container)
      expect(images).toHaveLength(1)
      expect(images[0]).toHaveAttribute('alt', 'Test Pack')
    })

    it('renders hover highlight when enableHoverHighlight is true', () => {
      const { container } = render(<ThemePackCard {...defaultProps} enableHoverHighlight />)

      const srcs = getSrcs(container)
      expect(srcs).toHaveLength(2)
      expect(srcs).toContain('/images/UI/themePack/onHover.webp')
    })

    it('renders both select and hover layers when isSelected is true', () => {
      const { container } = render(<ThemePackCard {...defaultProps} isSelected />)

      const srcs = getSrcs(container)
      expect(srcs).toHaveLength(3)
      expect(srcs).toContain('/images/UI/themePack/onSelect.webp')
      expect(srcs).toContain('/images/UI/themePack/onHover.webp')
    })

    it('renders hover layer after select layer for z-order priority', () => {
      const { container } = render(<ThemePackCard {...defaultProps} isSelected enableHoverHighlight />)

      const srcs = getSrcs(container)
      expect(srcs).toHaveLength(3)
      expect(srcs[0]).toBe('/images/themePack/pack1.webp')
      expect(srcs[1]).toBe('/images/UI/themePack/onSelect.webp')
      expect(srcs[2]).toBe('/images/UI/themePack/onHover.webp')
    })

    it('uses extreme highlight for both layers when pack is extreme', () => {
      const { container } = render(
        <ThemePackCard
          {...defaultProps}
          packEntry={extremePackEntry}
          isSelected
          enableHoverHighlight
        />
      )

      const srcs = getSrcs(container)
      expect(srcs).toHaveLength(3)
      expect(srcs[1]).toBe('/images/UI/themePack/extremeHighlight.webp')
      expect(srcs[2]).toBe('/images/UI/themePack/extremeHighlight.webp')
    })

    it('hover layer starts invisible with CSS hover transition', () => {
      const { container } = render(<ThemePackCard {...defaultProps} enableHoverHighlight />)

      const images = getImages(container)
      const hoverLayer = images[1]
      expect(hoverLayer.className).toContain('opacity-0')
      expect(hoverLayer.className).toContain('group-hover:opacity-100')
    })

    it('select layer is always visible when rendered', () => {
      const { container } = render(<ThemePackCard {...defaultProps} isSelected />)

      const images = getImages(container)
      const selectLayer = images[1]
      expect(selectLayer).toHaveAttribute('src', '/images/UI/themePack/onSelect.webp')
      expect(selectLayer.className).not.toContain('opacity-0')
    })
  })
})
