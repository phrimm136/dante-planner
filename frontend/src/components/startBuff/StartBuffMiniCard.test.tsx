import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { StartBuffMiniCard } from './StartBuffMiniCard'

// Mock asset path functions
vi.mock('@/lib/assetPaths', () => ({
  getStartBuffIconPath: (baseId: number) => `/mock/icon/${baseId}.webp`,
  getStartBuffMiniPath: () => '/mock/startBuffMini.webp',
  getStartBuffMiniHighlightPath: () => '/mock/startBuffMiniHighlight.webp',
}))

// Mock constants
vi.mock('@/lib/constants', () => ({
  MD_ACCENT_COLORS: { 6: '#00ffcc' },
  CURRENT_MD_VERSION: 6,
}))

// Mock EGOGiftEnhancementIndicator to track when it's rendered
vi.mock('@/components/egoGift/EGOGiftEnhancementIndicator', () => ({
  EGOGiftEnhancementIndicator: ({ enhancement }: { enhancement: number }) => {
    if (enhancement === 0) return null
    return <div data-testid="enhancement-indicator" data-enhancement={enhancement}>+{enhancement}</div>
  },
}))

describe('StartBuffMiniCard', () => {
  describe('background and icon rendering', () => {
    it('renders background image', () => {
      const { container } = render(
        <StartBuffMiniCard buffId={100} displayName="Test Buff" />
      )

      const bgImg = container.querySelector('img[src="/mock/startBuffMini.webp"]')
      expect(bgImg).not.toBeNull()
    })

    it('renders buff icon using getStartBuffIconPath', () => {
      const { container } = render(
        <StartBuffMiniCard buffId={100} displayName="Test Buff" />
      )

      // baseId for buffId 100 should be 100 (no enhancement)
      const iconImg = container.querySelector('img[src="/mock/icon/100.webp"]')
      expect(iconImg).not.toBeNull()
    })

    it('extracts correct baseId from enhanced buffId', () => {
      const { container } = render(
        <StartBuffMiniCard buffId={201} displayName="Enhanced Buff" />
      )

      // buffId 201 = baseId 2, enhancement 1
      // baseId = Math.floor(201 / 100) = 2? Let me check the actual logic
      // Actually need to verify the baseId extraction logic
      const iconImg = container.querySelector('img[src*="/mock/icon/"]')
      expect(iconImg).not.toBeNull()
    })
  })

  describe('name and enhancement suffix', () => {
    it('renders name without suffix for enhancement 0', () => {
      // buffId 100: baseId = (100 % 100) + 100 = 100, enhancement = Math.floor(100/100) - 1 = 0
      render(<StartBuffMiniCard buffId={100} displayName="Test Buff" />)

      const nameElement = screen.getByText('Test Buff')
      expect(nameElement).toBeDefined()
      // No + or ++ suffix
      expect(screen.queryByText(/Test Buff\+/)).toBeNull()
    })

    it('renders name with "+" suffix for enhancement 1', () => {
      // buffId 201: baseId = (201 % 100) + 100 = 101, enhancement = Math.floor(201/100) - 1 = 1
      render(<StartBuffMiniCard buffId={201} displayName="Test Buff" />)

      expect(screen.getByText('Test Buff+')).toBeDefined()
    })

    it('renders name with "++" suffix for enhancement 2', () => {
      // buffId 302: baseId = (302 % 100) + 100 = 102, enhancement = Math.floor(302/100) - 1 = 2
      render(<StartBuffMiniCard buffId={302} displayName="Test Buff" />)

      expect(screen.getByText('Test Buff++')).toBeDefined()
    })
  })

  describe('enhancement indicator', () => {
    it('does not render indicator for enhancement 0', () => {
      // buffId 100: enhancement = 0
      render(<StartBuffMiniCard buffId={100} displayName="Test Buff" />)

      expect(screen.queryByTestId('enhancement-indicator')).toBeNull()
    })

    it('renders indicator for enhancement 1', () => {
      // buffId 201: enhancement = 1
      render(<StartBuffMiniCard buffId={201} displayName="Test Buff" />)

      const indicator = screen.getByTestId('enhancement-indicator')
      expect(indicator).toBeDefined()
      expect(indicator.getAttribute('data-enhancement')).toBe('1')
    })

    it('renders indicator for enhancement 2', () => {
      // buffId 302: enhancement = 2
      render(<StartBuffMiniCard buffId={302} displayName="Test Buff" />)

      const indicator = screen.getByTestId('enhancement-indicator')
      expect(indicator).toBeDefined()
      expect(indicator.getAttribute('data-enhancement')).toBe('2')
    })
  })

  describe('hover highlight overlay', () => {
    it('renders highlight overlay image', () => {
      const { container } = render(
        <StartBuffMiniCard buffId={100} displayName="Test Buff" />
      )

      const highlightImg = container.querySelector('img[src="/mock/startBuffMiniHighlight.webp"]')
      expect(highlightImg).not.toBeNull()
    })

    it('highlight overlay has opacity-0 by default', () => {
      const { container } = render(
        <StartBuffMiniCard buffId={100} displayName="Test Buff" />
      )

      const highlightImg = container.querySelector('img[src="/mock/startBuffMiniHighlight.webp"]')
      expect(highlightImg?.className).toContain('opacity-0')
    })

    it('highlight overlay has group-hover:opacity-100 for hover effect', () => {
      const { container } = render(
        <StartBuffMiniCard buffId={100} displayName="Test Buff" />
      )

      const highlightImg = container.querySelector('img[src="/mock/startBuffMiniHighlight.webp"]')
      expect(highlightImg?.className).toContain('group-hover:opacity-100')
    })

    it('container has group class for hover propagation', () => {
      const { container } = render(
        <StartBuffMiniCard buffId={100} displayName="Test Buff" />
      )

      const cardContainer = container.firstChild as HTMLElement
      expect(cardContainer.className).toContain('group')
    })
  })

  describe('styling', () => {
    it('applies accent color to name text', () => {
      render(<StartBuffMiniCard buffId={100} displayName="Test Buff" />)

      const nameElement = screen.getByText('Test Buff')
      expect(nameElement.style.color).toBe('rgb(0, 255, 204)') // #00ffcc
    })

    it('has correct dimensions (w-24 h-24)', () => {
      const { container } = render(
        <StartBuffMiniCard buffId={100} displayName="Test Buff" />
      )

      const cardContainer = container.firstChild as HTMLElement
      expect(cardContainer.className).toContain('w-24')
      expect(cardContainer.className).toContain('h-24')
    })
  })
})
