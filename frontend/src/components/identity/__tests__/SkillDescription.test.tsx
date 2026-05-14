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
import type { IdentitySkillDescEntry } from '@/types/IdentityTypes'

// Asset path helper is used by the coin row renderer.
vi.mock('@/lib/assetPaths', () => ({
  getCoinDescIconPath: (i: number) => `/icons/coin-${i + 1}.webp`,
}))

// FormattedDescription renders text through a keyword-aware pipeline that
// requires its own data sources. For these tests we only care that the raw
// text reaches the DOM, so we replace it with a passthrough.
vi.mock('@/components/common/FormattedDescription', () => ({
  FormattedDescription: ({ text }: { text: string }) => <span>{text}</span>,
}))

describe('SkillDescription', () => {
  const baseDesc: IdentitySkillDescEntry = {
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
      render(
        <SkillDescription descData={baseDesc} flavor="lore" />,
      )

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
      const { container } = render(
        <SkillDescription descData={baseDesc} flavor="lore" />,
      )

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
})
