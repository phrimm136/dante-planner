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

      // Should have level label and input
      expect(screen.getByText('Level')).toBeDefined()
      expect(screen.getByRole('spinbutton')).toBeDefined()
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

    it('calls onLevelChange when level input changes', () => {
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

      const input = screen.getByRole('spinbutton')
      fireEvent.change(input, { target: { value: '30' } })
      expect(onLevelChange).toHaveBeenCalledWith(30)
    })

    it('clamps level input to valid range on blur', () => {
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

      const input = screen.getByRole('spinbutton')

      // Try to set value above MAX_LEVEL
      fireEvent.change(input, { target: { value: '100' } })
      fireEvent.blur(input)

      // Should clamp to MAX_LEVEL
      expect(onLevelChange).toHaveBeenLastCalledWith(MAX_LEVEL)
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

      // Should have 3 enhancement buttons
      const buttons = screen.getAllByRole('button')
      expect(buttons.length).toBe(3)

      // First button should show "-" for base level
      expect(screen.getByText('-')).toBeDefined()
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
