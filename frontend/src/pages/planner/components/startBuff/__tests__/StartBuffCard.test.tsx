import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { StartBuffCard } from '../StartBuffCard'
import type { StartBuff, StartBuffI18n, EnhancementLevel } from '@/shared/gameText'

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'EN' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

// Mock asset path functions — arguments are reflected so version/level routing is visible
vi.mock('@/shared/assets', () => ({
  getStartBuffIconPath: (baseId: number, version: number) => `/icon/${baseId}/${version}.png`,
  getStartBuffPanePath: (version: number) => `/pane/${version}.png`,
  getStartBuffHighlightPath: (version: number) => `/highlight/${version}.png`,
  getStartBuffStarLightPath: () => '/star.png',
  getStartBuffEnhancementBgPath: (lvl: number, version: number) => `/bg/${lvl}/${version}.png`,
  getStartBuffEnhancementOverlayPath: (version: number) => `/overlay/${version}.png`,
  getStartBuffEnhancementIconPath: (lvl: number) => `/enhance/${lvl}.png`,
}))

// Mock formatBuffEffects to return simple text
vi.mock('../formatBuffDescription', () => ({
  formatBuffEffects: () => <span>Mock effect description</span>,
}))

// Mock AutoSizeText to render plain text
vi.mock('@/components/ui/AutoSizeText', () => ({
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

function renderCard(overrides: Partial<Parameters<typeof StartBuffCard>[0]> = {}) {
  const props = {
    mdVersion: 6,
    buff: mockBuff,
    allBuffs: [mockBuff],
    i18n: mockI18n,
    isSelected: false,
    onSelect: vi.fn(),
    enhancement: 0 as EnhancementLevel,
    onEnhancementChange: vi.fn(),
    ...overrides,
  }
  return { props, ...render(<StartBuffCard {...props} />) }
}

describe('StartBuffCard', () => {
  it('renders the card button alongside its two enhancement buttons', () => {
    renderCard()

    expect(screen.getByRole('button', { name: 'Test Buff' })).toBeInTheDocument()
    expect(screen.getAllByRole('button')).toHaveLength(3)
  })

  it('calls onSelect when card is clicked', () => {
    const onSelect = vi.fn()
    renderCard({ onSelect })

    fireEvent.click(screen.getByRole('button', { name: 'Test Buff' }))

    expect(onSelect).toHaveBeenCalledWith(100, true)
  })

  it('calls onSelect with selected=false when deselecting', () => {
    const onSelect = vi.fn()
    renderCard({ onSelect, isSelected: true })

    fireEvent.click(screen.getByRole('button', { name: 'Test Buff' }))

    expect(onSelect).toHaveBeenCalledWith(100, false)
  })

  it('shows selection highlight when selected', () => {
    const { container } = renderCard({ isSelected: true })

    expect(container.querySelector('img[src="/highlight/6.png"]')).not.toBeNull()
  })

  it('resolves every asset through the requested MD version', () => {
    const { container } = renderCard({ mdVersion: 7 })

    expect(container.querySelector('img[src="/pane/7.png"]')).not.toBeNull()
    expect(container.querySelector('img[src="/icon/100/7.png"]')).not.toBeNull()
    expect(container.querySelector('img[src="/highlight/7.png"]')).not.toBeNull()
  })

  it('draws the selected-state overlay frame only for MD7', () => {
    const { container: md7 } = renderCard({ mdVersion: 7, enhancement: 1 })
    expect(md7.innerHTML).toContain('/overlay/7.png')

    const { container: md6 } = renderCard({ mdVersion: 6, enhancement: 1 })
    expect(md6.innerHTML).not.toContain('/overlay/')
  })

  it('clears the press timer on unmount', () => {
    vi.useFakeTimers()
    const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout')
    const { unmount } = renderCard()

    fireEvent.click(screen.getByRole('button', { name: 'Test Buff' }))
    unmount()

    expect(clearTimeoutSpy).toHaveBeenCalled()
    clearTimeoutSpy.mockRestore()
    vi.useRealTimers()
  })

  // Locks the per-variant layout: any class, offset or border-image change shows up here
  it.each([6, 7])('matches the MD%i layout snapshot', (mdVersion) => {
    const { container } = renderCard({ mdVersion, enhancement: 1, isSelected: true })

    expect(container.innerHTML).toMatchSnapshot()
  })

  it('falls back to the MD6 layout for an unknown version', () => {
    const { container: unknown } = renderCard({ mdVersion: 99 })
    const { container: md6 } = renderCard({ mdVersion: 6 })

    expect(unknown.innerHTML).toBe(md6.innerHTML)
  })
})
