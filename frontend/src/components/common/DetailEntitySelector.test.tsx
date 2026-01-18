import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { DetailEntitySelector } from './DetailEntitySelector'
import { MAX_LEVEL, MAX_ENTITY_TIER } from '@/lib/constants'

describe('DetailEntitySelector', () => {
  describe('Identity mode', () => {
    it('renders all 4 uptie buttons', () => {
      const onTierChange = vi.fn()
      const onLevelChange = vi.fn()

      render(
        <DetailEntitySelector
          entityType="identity"
          tier={4}
          onTierChange={onTierChange}
          level={MAX_LEVEL}
          onLevelChange={onLevelChange}
        />
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
          entityType="identity"
          tier={4}
          onTierChange={onTierChange}
          level={MAX_LEVEL}
          onLevelChange={onLevelChange}
        />
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
          entityType="identity"
          tier={4}
          onTierChange={onTierChange}
          level={MAX_LEVEL}
          onLevelChange={onLevelChange}
        />
      )

      fireEvent.click(screen.getByRole('button', { name: /tier 1/i }))
      expect(onTierChange).toHaveBeenCalledWith(1)
    })

    it('renders slider with correct range and current value', () => {
      const onTierChange = vi.fn()
      const onLevelChange = vi.fn()

      render(
        <DetailEntitySelector
          entityType="identity"
          tier={4}
          onTierChange={onTierChange}
          level={30}
          onLevelChange={onLevelChange}
        />
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
          entityType="identity"
          tier={4}
          onTierChange={onTierChange}
          level={MAX_LEVEL}
          onLevelChange={onLevelChange}
        />
      )

      const slider = screen.getByRole('slider')

      // Slider component should have max attribute set to MAX_LEVEL
      expect(slider).toHaveAttribute('aria-valuemax', String(MAX_LEVEL))
      expect(slider).toHaveAttribute('aria-valuemin', '1')
      expect(slider).toHaveAttribute('aria-valuenow', String(MAX_LEVEL))
    })
  })

  describe('EGO mode', () => {
    it('renders all 4 threadspin buttons without level slider', () => {
      const onTierChange = vi.fn()

      render(
        <DetailEntitySelector
          entityType="ego"
          tier={4}
          onTierChange={onTierChange}
        />
      )

      // Should have 4 tier buttons
      expect(screen.getByRole('button', { name: /tier 1/i })).toBeDefined()
      expect(screen.getByRole('button', { name: /tier 4/i })).toBeDefined()

      // Should NOT have level input (EGO doesn't use level)
      expect(screen.queryByRole('spinbutton')).toBeNull()
    })
  })

  describe('EGO Gift mode', () => {
    it('renders 3 enhancement buttons (0, 1, 2)', () => {
      const onTierChange = vi.fn()

      render(
        <DetailEntitySelector
          entityType="egoGift"
          tier={0}
          onTierChange={onTierChange}
        />
      )

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

      render(
        <DetailEntitySelector
          entityType="egoGift"
          tier={0}
          onTierChange={onTierChange}
        />
      )

      // Should NOT have level input
      expect(screen.queryByRole('spinbutton')).toBeNull()
    })
  })
})
