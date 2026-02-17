import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { StartBuffCard } from './StartBuffCard'
import type { StartBuff, StartBuffI18n } from '@/types/StartBuffTypes'

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ i18n: { language: 'EN' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

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
vi.mock('@/components/common/AutoSizeText', () => ({
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
  it('renders enhancement buttons', () => {
    const onSelect = vi.fn()

    render(
      <StartBuffCard
        buff={mockBuff}
        allBuffs={[mockBuff]}
        i18n={mockI18n}
        isSelected={false}
        onSelect={onSelect}
        enhancement={0}
        onEnhancementChange={vi.fn()}
        mdVersion={5}
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
        enhancement={0}
        onEnhancementChange={vi.fn()}
        mdVersion={5}
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

  it('calls onSelect with negative ID when deselecting', () => {
    const onSelect = vi.fn()

    render(
      <StartBuffCard
        buff={mockBuff}
        allBuffs={[mockBuff]}
        i18n={mockI18n}
        isSelected={true}
        onSelect={onSelect}
        enhancement={0}
        onEnhancementChange={vi.fn()}
        mdVersion={5}
      />
    )

    // Click the card to deselect
    const card = screen.getByText('Test Buff').closest('.cursor-pointer')
    if (card) {
      fireEvent.click(card)
    }

    // onSelect should be called with negative ID to signal deselection
    expect(onSelect).toHaveBeenCalledWith(-100)
  })

  it('shows selection highlight when selected', () => {
    const onSelect = vi.fn()

    const { container } = render(
      <StartBuffCard
        buff={mockBuff}
        allBuffs={[mockBuff]}
        i18n={mockI18n}
        isSelected={true}
        onSelect={onSelect}
        enhancement={0}
        onEnhancementChange={vi.fn()}
        mdVersion={5}
      />
    )

    // Highlight image should be rendered - check for the specific mock path
    const highlightImg = container.querySelector('img[src="/mock/highlight.png"]')
    expect(highlightImg).not.toBeNull()
  })
})
