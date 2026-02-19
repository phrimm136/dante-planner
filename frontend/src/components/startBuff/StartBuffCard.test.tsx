import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { StartBuffCardMD6 as StartBuffCard } from './StartBuffCardMD6'
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
      />
    )

    expect(screen.getAllByRole('button')).toHaveLength(2)
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
      />
    )

    // Highlight image should be rendered - check for the specific mock path
    const highlightImg = container.querySelector('img[src="/mock/highlight.png"]')
    expect(highlightImg).not.toBeNull()
  })
})
