/**
 * FormattedKeyword.test.tsx
 *
 * Component tests for FormattedKeyword.
 * Tests rendering of different keyword types, popover behavior, and styling.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { FormattedKeyword } from '../FormattedKeyword'
import type { ResolvedKeyword } from '@/types/KeywordTypes'

// Mock asset path helper
vi.mock('@/lib/assetPaths', () => ({
  getBattleKeywordIconPath: (path: string) => `/icons/${path}.webp`,
}))

describe('FormattedKeyword', () => {
  describe('unknown keywords', () => {
    it('renders unknown keyword as plain text with brackets', () => {
      const keyword: ResolvedKeyword = {
        type: 'unknown',
        key: 'UnknownKeyword',
        displayText: 'UnknownKeyword',
      }

      render(<FormattedKeyword keyword={keyword} />)

      expect(screen.getByText('[UnknownKeyword]')).toBeInTheDocument()
    })

    it('does not render as button for unknown keywords', () => {
      const keyword: ResolvedKeyword = {
        type: 'unknown',
        key: 'Test',
        displayText: 'Test',
      }

      const { container } = render(<FormattedKeyword keyword={keyword} />)

      expect(container.querySelector('button')).toBeNull()
    })
  })

  describe('skill tag keywords', () => {
    it('renders skill tag with color', () => {
      const keyword: ResolvedKeyword = {
        type: 'skillTag',
        key: 'WhenUse',
        displayText: '[On Use]',
        color: '#00ff00',
      }

      render(<FormattedKeyword keyword={keyword} />)

      const element = screen.getByText('[On Use]')
      expect(element).toBeInTheDocument()
      expect(element).toHaveStyle({ color: '#00ff00' })
    })

    it('does not render popover for skill tags', () => {
      const keyword: ResolvedKeyword = {
        type: 'skillTag',
        key: 'Test',
        displayText: '[Test]',
        color: '#ffffff',
      }

      const { container } = render(<FormattedKeyword keyword={keyword} />)

      expect(container.querySelector('button')).toBeNull()
    })
  })

  describe('battle keywords', () => {
    const battleKeyword: ResolvedKeyword = {
      type: 'battleKeyword',
      key: 'Sinking',
      displayText: 'Sinking',
      description: 'Lose HP each turn based on Count',
      iconId: 'Sinking',
      buffType: 'Negative',
      color: '#4a9eff',
    }

    it('renders button trigger with icon and text', () => {
      render(<FormattedKeyword keyword={battleKeyword} />)

      const button = screen.getByRole('button')
      expect(button).toBeInTheDocument()
      expect(button).toHaveStyle({ color: '#4a9eff' })

      const icon = button.querySelector('img')
      expect(icon).toBeInTheDocument()
      expect(icon).toHaveAttribute('src', '/icons/Sinking.webp')
      expect(icon).toHaveAttribute('alt', '')
      expect(icon).toHaveClass('w-4', 'h-4')
    })

    it('renders keyword text in button', () => {
      render(<FormattedKeyword keyword={battleKeyword} />)

      expect(screen.getByText('Sinking')).toBeInTheDocument()
    })

    it('uses iconId if provided, otherwise falls back to key', () => {
      const keywordWithIcon: ResolvedKeyword = {
        ...battleKeyword,
        key: 'Test',
        iconId: 'CustomIcon',
      }

      const { container } = render(<FormattedKeyword keyword={keywordWithIcon} />)
      const icon = container.querySelector('img')
      expect(icon).toHaveAttribute('src', '/icons/CustomIcon.webp')
    })

    it('uses key for icon path when iconId is null', () => {
      const keywordNoIcon: ResolvedKeyword = {
        ...battleKeyword,
        iconId: null,
      }

      const { container } = render(<FormattedKeyword keyword={keywordNoIcon} />)
      const icon = container.querySelector('img')
      expect(icon).toHaveAttribute('src', '/icons/Sinking.webp')
    })
  })

  describe('popover content', () => {
    const battleKeyword: ResolvedKeyword = {
      type: 'battleKeyword',
      key: 'Burn',
      displayText: 'Burn',
      description: 'Take damage at turn end',
      iconId: 'Burn',
      color: '#ff6b00',
    }

    it('renders popover with correct styling classes', async () => {
      const user = userEvent.setup()
      render(<FormattedKeyword keyword={battleKeyword} />)

      const button = screen.getByRole('button')
      await user.click(button)

      // Popover renders in Portal, use document.body
      const popoverContent = document.body.querySelector('[data-slot="popover-content"]')
      expect(popoverContent).toBeInTheDocument()
      expect(popoverContent).toHaveClass('bg-black/85')
      expect(popoverContent).toHaveClass('border-neutral-800')
      expect(popoverContent).toHaveClass('!animate-none')
    })

    it('displays keyword icon in popover header', async () => {
      const user = userEvent.setup()
      render(<FormattedKeyword keyword={battleKeyword} />)

      await user.click(screen.getByRole('button'))

      // Popover renders in Portal
      const popover = document.body.querySelector('[data-slot="popover-content"]')
      const icon = popover?.querySelector('img')
      expect(icon).toBeInTheDocument()
      expect(icon).toHaveClass('w-6', 'h-6')
      expect(icon).toHaveAttribute('src', '/icons/Burn.webp')
    })

    it('displays keyword name without custom color styling', async () => {
      const user = userEvent.setup()
      render(<FormattedKeyword keyword={battleKeyword} />)

      await user.click(screen.getByRole('button'))

      // Find h4 within popover (rendered in Portal)
      const popover = document.body.querySelector('[data-slot="popover-content"]')
      const popoverTitle = popover?.querySelector('h4')
      expect(popoverTitle).toBeInTheDocument()
      expect(popoverTitle).toHaveClass('font-bold', 'text-lg')
      expect(popoverTitle).toHaveTextContent('Burn')
      // Should not have inline style attribute
      expect(popoverTitle).not.toHaveAttribute('style')
    })

    it('displays description text', async () => {
      const user = userEvent.setup()
      render(<FormattedKeyword keyword={battleKeyword} />)

      await user.click(screen.getByRole('button'))

      expect(screen.getByText('Take damage at turn end')).toBeInTheDocument()
    })

    it('handles missing description gracefully', async () => {
      const user = userEvent.setup()
      const keywordNoDesc: ResolvedKeyword = {
        ...battleKeyword,
        description: undefined,
      }

      render(<FormattedKeyword keyword={keywordNoDesc} />)

      await user.click(screen.getByRole('button'))

      // Should still render title but no description paragraph (Portal)
      const popover = document.body.querySelector('[data-slot="popover-content"]')
      expect(popover?.querySelector('p')).toBeNull()
    })

    it('applies no animation classes', async () => {
      const user = userEvent.setup()
      render(<FormattedKeyword keyword={battleKeyword} />)

      await user.click(screen.getByRole('button'))

      // Popover rendered in Portal
      const popoverContent = document.body.querySelector('[data-slot="popover-content"]')
      expect(popoverContent).toHaveClass('!animate-none')
      expect(popoverContent).toHaveClass('data-[state=open]:!animate-none')
      expect(popoverContent).toHaveClass('data-[state=closed]:!animate-none')
    })
  })

  describe('accessibility', () => {
    it('renders button with proper keyboard accessibility', () => {
      const keyword: ResolvedKeyword = {
        type: 'battleKeyword',
        key: 'Test',
        displayText: 'Test',
        description: 'Test description',
        color: '#ffffff',
      }

      render(<FormattedKeyword keyword={keyword} />)

      const button = screen.getByRole('button')
      expect(button).toHaveAttribute('type', 'button')
    })

    it('renders icon with aria-hidden for screen readers', () => {
      const keyword: ResolvedKeyword = {
        type: 'battleKeyword',
        key: 'Test',
        displayText: 'Test',
        iconId: 'Test',
        color: '#ffffff',
      }

      const { container } = render(<FormattedKeyword keyword={keyword} />)
      const icon = container.querySelector('img')
      expect(icon).toHaveAttribute('aria-hidden', 'true')
      expect(icon).toHaveAttribute('alt', '')
    })
  })

  describe('custom className', () => {
    it('applies custom className to wrapper', () => {
      const keyword: ResolvedKeyword = {
        type: 'unknown',
        key: 'Test',
        displayText: 'Test',
      }

      const { container } = render(
        <FormattedKeyword keyword={keyword} className="custom-class" />
      )

      expect(container.firstChild).toHaveClass('custom-class')
    })

    it('applies className to skill tag', () => {
      const keyword: ResolvedKeyword = {
        type: 'skillTag',
        key: 'Test',
        displayText: '[Test]',
        color: '#ffffff',
      }

      render(<FormattedKeyword keyword={keyword} className="custom-class" />)

      const element = screen.getByText('[Test]')
      expect(element).toHaveClass('custom-class')
    })

    it('applies className to battle keyword button', () => {
      const keyword: ResolvedKeyword = {
        type: 'battleKeyword',
        key: 'Test',
        displayText: 'Test',
        color: '#ffffff',
      }

      render(<FormattedKeyword keyword={keyword} className="custom-class" />)

      const button = screen.getByRole('button')
      expect(button).toHaveClass('custom-class')
    })
  })
})
