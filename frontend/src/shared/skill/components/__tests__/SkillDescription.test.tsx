/**
 * SkillDescription.test.tsx
 *
 * Component tests for SkillDescription.
 * Covers desc rendering, coin descriptions, and the per-skill flavor lore line
 * that mirrors the in-game `[Text]SkillInfoFlavor` TMP element.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { SkillDescription } from '../SkillDescription'
import { FLAVOR_TEXT_COLOR } from '@/lib/constants'
import type { SkillDescEntry } from '@/shared/gameData'

// Asset path helper is used by the coin row renderer.
vi.mock('@/shared/assets', () => ({
  getCoinDescIconPath: (i: number) => `/icons/coin-${i + 1}.webp`,
}))

// FormattedDescription renders text through a keyword-aware pipeline that
// requires its own data sources. For these tests we only care that the raw
// text reaches the DOM, so we replace it with a passthrough.
vi.mock('@/shared/gameText/components/FormattedDescription', () => ({
  FormattedDescription: ({ text }: { text: string }) => <span>{text}</span>,
}))

describe('SkillDescription', () => {
  const baseDesc: SkillDescEntry = {
    desc: 'Inflict +1 Sinking Count',
    coinDescs: ['On Hit: gain 1 Sinking'],
  }

  describe('flavor rendering', () => {
    it('renders flavor when provided', () => {
      render(
        <SkillDescription
          descData={baseDesc}
          flavor="A technique that cuts through space itself."
        />,
      )

      const flavor = screen.getByTestId('skill-flavor')
      expect(flavor).toBeInTheDocument()
      expect(flavor).toHaveTextContent('A technique that cuts through space itself.')
    })

    it('omits the flavor element when flavor is undefined', () => {
      render(<SkillDescription descData={baseDesc} />)
      expect(screen.queryByTestId('skill-flavor')).not.toBeInTheDocument()
    })

    it('omits the flavor element when flavor is an empty string', () => {
      render(<SkillDescription descData={baseDesc} flavor="" />)
      expect(screen.queryByTestId('skill-flavor')).not.toBeInTheDocument()
    })

    it('applies the FLAVOR_TEXT_COLOR brand color', () => {
      render(<SkillDescription descData={baseDesc} flavor="lore" />)

      const flavor = screen.getByTestId('skill-flavor')
      // jsdom serializes hex into rgb(...) when comparing inline styles.
      expect(flavor).toHaveStyle({ color: FLAVOR_TEXT_COLOR })
    })

    it('renders flavor as the last child of the description container', () => {
      const { container } = render(
        <SkillDescription descData={baseDesc} flavor="end-of-block lore" />,
      )

      // Container = the SkillTextBox-equivalent wrapper.
      const wrapper = container.firstChild as HTMLElement
      const lastChild = wrapper.lastElementChild as HTMLElement
      expect(lastChild).toBe(screen.getByTestId('skill-flavor'))
    })

    it('preserves multi-line flavor via whitespace-pre-line', () => {
      const multiLine = 'First line.\nSecond line.'
      render(<SkillDescription descData={baseDesc} flavor={multiLine} />)

      const flavor = screen.getByTestId('skill-flavor')
      expect(flavor).toHaveClass('whitespace-pre-line')
      expect(flavor.textContent).toBe(multiLine)
    })
  })

  describe('desc + coin rendering remains intact', () => {
    it('renders the main desc above the flavor', () => {
      const { container } = render(<SkillDescription descData={baseDesc} flavor="lore" />)

      expect(screen.getByText('Inflict +1 Sinking Count')).toBeInTheDocument()
      const wrapper = container.firstChild as HTMLElement
      const descNode = screen.getByText('Inflict +1 Sinking Count')
      const flavorNode = screen.getByTestId('skill-flavor')

      // DOM order: desc precedes flavor inside the same container.
      const children = Array.from(wrapper.children)
      const descIndex = children.findIndex((c) => c.contains(descNode))
      const flavorIndex = children.indexOf(flavorNode)
      expect(descIndex).toBeGreaterThanOrEqual(0)
      expect(flavorIndex).toBeGreaterThan(descIndex)
    })

    it('renders coin descriptions between desc and flavor', () => {
      render(<SkillDescription descData={baseDesc} flavor="lore" />)

      expect(screen.getByText('On Hit: gain 1 Sinking')).toBeInTheDocument()
      expect(screen.getByAltText('Coin 1')).toBeInTheDocument()
    })

    it('handles empty descData gracefully', () => {
      render(<SkillDescription descData={{}} flavor="lore" />)
      expect(screen.getByTestId('skill-flavor')).toBeInTheDocument()
    })
  })

  describe('client lower-block geometry', () => {
    const twoCoins: SkillDescEntry = {
      desc: 'Inflict +1 Sinking Count',
      coinDescs: ['On Hit: gain 1 Sinking', 'On Hit: gain 2 Sinking'],
    }

    it('lays out the coin list in client units with contiguous rows', () => {
      const { container } = render(<SkillDescription descData={twoCoins} />)

      const list = container.querySelector('[data-slot="skill-coin-list"]') as HTMLElement
      expect(list.style.paddingTop).toBe('0.2712rem')
      expect(list.style.paddingBottom).toBe('0.9319rem')
      expect(list.className).not.toMatch(/space-y|gap/)

      const rows = Array.from(list.children) as HTMLElement[]
      expect(rows).toHaveLength(2)
      for (const row of rows) {
        expect(row.style.minHeight).toBe('2.016rem')
        expect(row.style.margin).toBe('')
        expect(row.className).not.toMatch(/\bm[ty]?-|space-y|gap/)

        const icon = row.querySelector('img') as HTMLImageElement
        expect(icon).toHaveClass('absolute')
        expect(icon.style.left).toBe('-0.1137rem')
        expect(icon.style.top).toBe('0px')
        expect(icon.style.width).toBe('2.016rem')
        expect(icon.style.height).toBe('2.016rem')

        const text = icon.nextElementSibling as HTMLElement
        expect(text.style.padding).toBe('0.6869rem 0.4813rem 0.1881rem 2.2925rem')
      }
    })

    it('renders no coin list without coin descs', () => {
      const { container } = render(<SkillDescription descData={{ desc: 'only desc' }} />)
      expect(container.querySelector('[data-slot="skill-coin-list"]')).toBeNull()
    })

    it('renders no coin list when every coin desc is empty', () => {
      const { container } = render(<SkillDescription descData={{ coinDescs: ['', ''] }} />)
      expect(container.querySelector('[data-slot="skill-coin-list"]')).toBeNull()
    })

    it('skips empty coin descs and keeps the coin number of the rest', () => {
      render(<SkillDescription descData={{ coinDescs: ['', 'second coin'] }} />)
      expect(screen.queryByAltText('Coin 1')).not.toBeInTheDocument()
      expect(screen.getByAltText('Coin 2')).toHaveAttribute('src', '/icons/coin-2.webp')
    })

    it('sizes the flavor at 33 units with 15 and 35 unit margins', () => {
      render(<SkillDescription descData={baseDesc} flavor="x" />)

      const flavor = screen.getByTestId('skill-flavor')
      expect(flavor.style.fontSize).toBe('0.7219rem')
      expect(flavor.style.marginTop).toBe('0.3281rem')
      expect(flavor.style.marginBottom).toBe('0.7656rem')
    })

    it('sits the description flush on the coin list', () => {
      const { container } = render(<SkillDescription descData={twoCoins} />)

      const wrapper = container.firstChild as HTMLElement
      expect(wrapper.className).not.toMatch(/space-y/)
      const descWrapper = wrapper.firstElementChild as HTMLElement
      expect(descWrapper).toHaveTextContent('Inflict +1 Sinking Count')
      expect(descWrapper.className).not.toMatch(/\bp[by]?-/)
      expect(descWrapper.style.paddingBottom).toBe('')
    })
  })
})
