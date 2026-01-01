import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { StartBuffCard } from './StartBuffCard'
import type { StartBuff, StartBuffI18n } from '@/types/StartBuffTypes'

// Mock asset path functions
vi.mock('@/lib/assetPaths', () => ({
  getStartBuffIconPath: () => '/mock/icon.png',
  getStartBuffPanePath: () => '/mock/pane.png',
  getStartBuffHighlightPath: () => '/mock/highlight.png',
  getStartBuffStarLightPath: () => '/mock/star.png',
  getStartBuffEnhancementBgPath: () => '/mock/enhance-bg.png',
  getStartBuffEnhancementIconPath: () => '/mock/enhance-icon.png',
}))

// Mock formatBuffEffects to return simple text
vi.mock('./formatBuffDescription', () => ({
  formatBuffEffects: () => <span>Mock effect description</span>,
}))

// Mock AutoSizeText to render plain text
vi.mock('./AutoSizeText', () => ({
  AutoSizeText: ({ text }: { text: string }) => <span>{text}</span>,
}))

// Mock EnhancementButton to render a simple button
vi.mock('./EnhancementButton', () => ({
  EnhancementButton: ({ level, onClick }: { level: number; onClick: () => void }) => (
    <button data-testid={`enhancement-btn-${level}`} onClick={onClick}>
      {level === 1 ? '+' : '++'}
    </button>
  ),
}))

const mockBuff: StartBuff = {
  id: '100',
  baseId: 100,
  level: 0,
  name: 'Test Buff',
  cost: 3,
  effects: [],
  iconSpriteId: 'test_icon',
}

const mockI18n: StartBuffI18n = {}

describe('StartBuffCard', () => {
  describe('default mode (viewMode=false)', () => {
    it('renders enhancement buttons', () => {
      const onSelect = vi.fn()

      render(
        <StartBuffCard
          buff={mockBuff}
          allBuffs={[mockBuff]}
          i18n={mockI18n}
          isSelected={false}
          onSelect={onSelect}
        />
      )

      // Enhancement buttons should be visible
      expect(screen.getByTestId('enhancement-btn-1')).toBeDefined()
      expect(screen.getByTestId('enhancement-btn-2')).toBeDefined()
    })

    it('calls onSelect when card is clicked', () => {
      const onSelect = vi.fn()

      render(
        <StartBuffCard
          buff={mockBuff}
          allBuffs={[mockBuff]}
          i18n={mockI18n}
          isSelected={false}
          onSelect={onSelect}
        />
      )

      // Click the card (the outer div with cursor-pointer)
      const card = screen.getByText('Test Buff').closest('.cursor-pointer')
      if (card) {
        fireEvent.click(card)
      }

      // onSelect should be called with the buff ID (baseId + enhancement creates 100)
      expect(onSelect).toHaveBeenCalledWith(100)
    })
  })

  describe('view mode (viewMode=true)', () => {
    it('hides enhancement buttons (UT1)', () => {
      const onSelect = vi.fn()

      render(
        <StartBuffCard
          buff={mockBuff}
          allBuffs={[mockBuff]}
          i18n={mockI18n}
          isSelected={false}
          onSelect={onSelect}
          viewMode={true}
        />
      )

      // In viewMode, enhancement buttons should not be rendered
      expect(screen.queryByTestId('enhancement-btn-1')).toBeNull()
      expect(screen.queryByTestId('enhancement-btn-2')).toBeNull()
    })

    it('does not call onSelect when card is clicked (UT2)', () => {
      const onSelect = vi.fn()

      render(
        <StartBuffCard
          buff={mockBuff}
          allBuffs={[mockBuff]}
          i18n={mockI18n}
          isSelected={false}
          onSelect={onSelect}
          viewMode={true}
        />
      )

      // Click the card
      const card = screen.getByText('Test Buff').closest('.cursor-pointer')
      if (card) {
        fireEvent.click(card)
      }

      // onSelect should NOT be called in viewMode
      expect(onSelect).not.toHaveBeenCalled()
    })

    it('still shows selection highlight when selected', () => {
      const onSelect = vi.fn()

      const { container } = render(
        <StartBuffCard
          buff={mockBuff}
          allBuffs={[mockBuff]}
          i18n={mockI18n}
          isSelected={true}
          onSelect={onSelect}
          viewMode={true}
        />
      )

      // Highlight image should be rendered - check for the specific mock path
      const highlightImg = container.querySelector('img[src="/mock/highlight.png"]')
      expect(highlightImg).not.toBeNull()
    })
  })
})
