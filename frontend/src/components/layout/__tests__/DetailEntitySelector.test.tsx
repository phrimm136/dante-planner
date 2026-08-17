import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { DetailEntitySelector } from '../DetailEntitySelector'
import { getEGOTierIconPath } from '@/shared/assets'
import { MAX_LEVEL, MAX_ENTITY_TIER, MIN_ENTITY_TIER } from '@/shared/gameData'

const IDENTITY_BOUNDS = {
  tierLabel: 'Uptie',
  minTier: MIN_ENTITY_TIER.identity,
  maxTier: MAX_ENTITY_TIER.identity,
  tierIconPath: getEGOTierIconPath,
}

const EGO_BOUNDS = {
  tierLabel: 'Threadspin',
  minTier: MIN_ENTITY_TIER.ego,
  tierIconPath: getEGOTierIconPath,
}

const GIFT_BOUNDS = {
  tierLabel: 'Enhancement',
  minTier: MIN_ENTITY_TIER.egoGift,
  maxTier: MAX_ENTITY_TIER.egoGift,
  tierIconPath: getEGOTierIconPath,
}

describe('DetailEntitySelector', () => {
  describe('Identity mode', () => {
    it('renders all 4 uptie buttons', () => {
      const onTierChange = vi.fn()
      const onLevelChange = vi.fn()

      render(
        <DetailEntitySelector
          {...IDENTITY_BOUNDS}
          tier={4}
          onTierChange={onTierChange}
          level={MAX_LEVEL}
          onLevelChange={onLevelChange}
        />,
      )

      // Should have 4 tier buttons (1, 2, 3, 4)
      expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /tier 2/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /tier 3/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /tier 4/i })).toBeDefined()
    })

    it('renders level slider and input', () => {
      const onTierChange = vi.fn()
      const onLevelChange = vi.fn()

      render(
        <DetailEntitySelector
          {...IDENTITY_BOUNDS}
          tier={4}
          onTierChange={onTierChange}
          level={MAX_LEVEL}
          onLevelChange={onLevelChange}
        />,
      )

      // Should have level label (Lv. X format) and slider
      expect(screen.getByText(`Lv. ${MAX_LEVEL}`)).toBeDefined()
      expect(screen.getByRole('slider')).toBeDefined()
    })

    it('calls onTierChange when tier button clicked', () => {
      const onTierChange = vi.fn()
      const onLevelChange = vi.fn()

      render(
        <DetailEntitySelector
          {...IDENTITY_BOUNDS}
          tier={4}
          onTierChange={onTierChange}
          level={MAX_LEVEL}
          onLevelChange={onLevelChange}
        />,
      )

      fireEvent.click(screen.getByRole('button', { name: /tier 1/i }))
      expect(onTierChange).toHaveBeenCalledWith(1)
    })

    it('renders slider with correct range and current value', () => {
      const onTierChange = vi.fn()
      const onLevelChange = vi.fn()

      render(
        <DetailEntitySelector
          {...IDENTITY_BOUNDS}
          tier={4}
          onTierChange={onTierChange}
          level={30}
          onLevelChange={onLevelChange}
        />,
      )

      const slider = screen.getByRole('slider')
      // Slider should be configured with correct range and current value
      expect(slider).toHaveAttribute('aria-valuemin', '1')
      expect(slider).toHaveAttribute('aria-valuemax', String(MAX_LEVEL))
      expect(slider).toHaveAttribute('aria-valuenow', '30')
    })

    it('constrains level slider to valid range', () => {
      const onTierChange = vi.fn()
      const onLevelChange = vi.fn()

      render(
        <DetailEntitySelector
          {...IDENTITY_BOUNDS}
          tier={4}
          onTierChange={onTierChange}
          level={MAX_LEVEL}
          onLevelChange={onLevelChange}
        />,
      )

      const slider = screen.getByRole('slider')

      // Slider component should have max attribute set to MAX_LEVEL
      expect(slider).toHaveAttribute('aria-valuemax', String(MAX_LEVEL))
      expect(slider).toHaveAttribute('aria-valuemin', '1')
      expect(slider).toHaveAttribute('aria-valuenow', String(MAX_LEVEL))
    })

    it('renders the caller-supplied tier label', () => {
      render(
        <DetailEntitySelector
          {...IDENTITY_BOUNDS}
          tier={4}
          onTierChange={vi.fn()}
          level={MAX_LEVEL}
          onLevelChange={vi.fn()}
        />,
      )

      expect(screen.getByText('Uptie')).toBeDefined()
    })
  })

  describe('EGO mode', () => {
    it('renders 4 threadspin buttons for a 4-cap EGO without level slider', () => {
      const onTierChange = vi.fn()

      render(
        <DetailEntitySelector {...EGO_BOUNDS} maxTier={4} tier={4} onTierChange={onTierChange} />,
      )

      expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /tier 4/i })).toBeDefined()
      expect(screen.queryByRole('button', { name: /tier 5/i })).toBeNull()
      expect(screen.queryByRole('spinbutton')).toBeNull()
    })

    it('renders 5 threadspin buttons when maxTier={5}', () => {
      const onTierChange = vi.fn()

      render(
        <DetailEntitySelector {...EGO_BOUNDS} maxTier={5} tier={5} onTierChange={onTierChange} />,
      )

      expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /tier 4/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /tier 5/i })).toBeDefined()
    })

    it('renders up to the global MAX_ENTITY_TIER.ego (5) when given that cap', () => {
      const onTierChange = vi.fn()

      render(
        <DetailEntitySelector
          {...EGO_BOUNDS}
          maxTier={MAX_ENTITY_TIER.ego}
          tier={4}
          onTierChange={onTierChange}
        />,
      )

      expect(screen.getByRole('button', { name: /tier 5/i })).toBeDefined()
    })

    it('omits the level slider when no onLevelChange is given', () => {
      render(<DetailEntitySelector {...EGO_BOUNDS} maxTier={5} tier={5} onTierChange={vi.fn()} />)

      expect(screen.queryByRole('slider')).toBeNull()
    })
  })

  describe('EGO Gift mode', () => {
    it('renders 3 enhancement buttons (0, 1, 2)', () => {
      const onTierChange = vi.fn()

      render(<DetailEntitySelector {...GIFT_BOUNDS} tier={0} onTierChange={onTierChange} />)

      // Should have 3 enhancement buttons (tier 0, 1, 2)
      const buttons = screen.getAllByRole('button')
      expect(buttons.length).toBe(3)

      // Buttons should have tier labels
      expect(screen.getByRole('button', { name: /tier 0/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /tier 2/i })).toBeDefined()
    })

    it('does not render level slider', () => {
      const onTierChange = vi.fn()

      render(<DetailEntitySelector {...GIFT_BOUNDS} tier={0} onTierChange={onTierChange} />)

      // Should NOT have level input
      expect(screen.queryByRole('spinbutton')).toBeNull()
    })
  })
})
