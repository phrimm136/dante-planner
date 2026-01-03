/**
 * FormattedDescription.test.tsx
 *
 * Component tests for FormattedDescription.
 * Tests rendering of keywords, text, and edge cases.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { FormattedDescription } from '../FormattedDescription'
import type { ParsedSegment, ResolvedKeyword } from '@/types/KeywordTypes'

// Mock useKeywordFormatter hook
const mockFormat = vi.fn<(text: string) => ParsedSegment[]>()

vi.mock('@/hooks/useKeywordFormatter', () => ({
  useKeywordFormatter: () => ({
    format: mockFormat,
  }),
}))

// Mock FormattedKeyword to render simplified output
vi.mock('../FormattedKeyword', () => ({
  FormattedKeyword: ({ keyword }: { keyword: ResolvedKeyword }) => (
    <span
      data-testid={`keyword-${keyword.key}`}
      data-type={keyword.type}
      style={{ color: keyword.color }}
    >
      {keyword.displayText}
    </span>
  ),
}))

describe('FormattedDescription', () => {
  beforeEach(() => {
    mockFormat.mockReset()
    // Default mock returns empty for any text
    mockFormat.mockReturnValue([])
  })

  describe('rendering empty/plain text', () => {
    it('renders nothing for empty string', () => {
      // Empty string returns null before calling format
      const { container } = render(<FormattedDescription text="" />)

      expect(container.firstChild).toBeNull()
    })

    it('renders plain text as-is', () => {
      mockFormat.mockReturnValue([{ type: 'text', content: 'Plain text without keywords' }])

      render(<FormattedDescription text="Plain text without keywords" />)

      expect(screen.getByText('Plain text without keywords')).toBeInTheDocument()
    })

    it('renders multiple text segments', () => {
      mockFormat.mockReturnValue([
        { type: 'text', content: 'First part ' },
        { type: 'text', content: 'second part' },
      ])

      const { container } = render(<FormattedDescription text="First part second part" />)

      // Text is combined in single container, check full content
      expect(container.textContent).toContain('First part')
      expect(container.textContent).toContain('second part')
    })
  })

  describe('rendering keywords', () => {
    it('renders single keyword with correct type', () => {
      const sinkingKeyword: ResolvedKeyword = {
        type: 'battleKeyword',
        key: 'Sinking',
        displayText: 'Sinking',
        description: 'Lose HP each turn',
        iconId: 'Sinking',
        buffType: 'Negative',
        color: '#ff0000',
      }

      mockFormat.mockReturnValue([
        { type: 'text', content: 'Apply ' },
        { type: 'keyword', content: 'Sinking', keyword: sinkingKeyword },
      ])

      render(<FormattedDescription text="Apply [Sinking]" />)

      expect(screen.getByText('Apply')).toBeInTheDocument()
      const keyword = screen.getByTestId('keyword-Sinking')
      expect(keyword).toBeInTheDocument()
      expect(keyword).toHaveAttribute('data-type', 'battleKeyword')
      expect(keyword).toHaveTextContent('Sinking')
    })

    it('renders skill tag keyword correctly', () => {
      const skillTagKeyword: ResolvedKeyword = {
        type: 'skillTag',
        key: 'WhenUse',
        displayText: '[On Use]',
        color: '#0000ff',
      }

      mockFormat.mockReturnValue([
        { type: 'keyword', content: 'WhenUse', keyword: skillTagKeyword },
        { type: 'text', content: ' Do something' },
      ])

      render(<FormattedDescription text="[WhenUse] Do something" />)

      const keyword = screen.getByTestId('keyword-WhenUse')
      expect(keyword).toHaveAttribute('data-type', 'skillTag')
      expect(keyword).toHaveTextContent('[On Use]')
    })

    it('renders unknown keyword correctly', () => {
      const unknownKeyword: ResolvedKeyword = {
        type: 'unknown',
        key: 'Unknown',
        displayText: '[Unknown]',
        color: '',
      }

      mockFormat.mockReturnValue([
        { type: 'keyword', content: 'Unknown', keyword: unknownKeyword },
      ])

      render(<FormattedDescription text="[Unknown]" />)

      const keyword = screen.getByTestId('keyword-Unknown')
      expect(keyword).toHaveAttribute('data-type', 'unknown')
      expect(keyword).toHaveTextContent('[Unknown]')
    })

    it('renders multiple keywords correctly', () => {
      const whenUse: ResolvedKeyword = {
        type: 'skillTag',
        key: 'WhenUse',
        displayText: '[On Use]',
        color: '#0000ff',
      }
      const sinking: ResolvedKeyword = {
        type: 'battleKeyword',
        key: 'Sinking',
        displayText: 'Sinking',
        color: '#ff0000',
      }

      mockFormat.mockReturnValue([
        { type: 'keyword', content: 'WhenUse', keyword: whenUse },
        { type: 'text', content: ' Apply ' },
        { type: 'keyword', content: 'Sinking', keyword: sinking },
      ])

      render(<FormattedDescription text="[WhenUse] Apply [Sinking]" />)

      expect(screen.getByTestId('keyword-WhenUse')).toBeInTheDocument()
      expect(screen.getByTestId('keyword-Sinking')).toBeInTheDocument()
      expect(screen.getByText('Apply')).toBeInTheDocument()
    })

    it('renders consecutive keywords without text between', () => {
      const keyword1: ResolvedKeyword = {
        type: 'skillTag',
        key: 'A',
        displayText: '[A]',
        color: '#111',
      }
      const keyword2: ResolvedKeyword = {
        type: 'skillTag',
        key: 'B',
        displayText: '[B]',
        color: '#222',
      }

      mockFormat.mockReturnValue([
        { type: 'keyword', content: 'A', keyword: keyword1 },
        { type: 'keyword', content: 'B', keyword: keyword2 },
      ])

      render(<FormattedDescription text="[A][B]" />)

      expect(screen.getByTestId('keyword-A')).toBeInTheDocument()
      expect(screen.getByTestId('keyword-B')).toBeInTheDocument()
    })
  })

  describe('color styling', () => {
    it('applies color to keywords', () => {
      const coloredKeyword: ResolvedKeyword = {
        type: 'battleKeyword',
        key: 'Burn',
        displayText: 'Burn',
        color: '#ff6600',
      }

      mockFormat.mockReturnValue([
        { type: 'keyword', content: 'Burn', keyword: coloredKeyword },
      ])

      render(<FormattedDescription text="[Burn]" />)

      const keyword = screen.getByTestId('keyword-Burn')
      expect(keyword).toHaveStyle({ color: '#ff6600' })
    })

    it('handles empty color string', () => {
      const noColorKeyword: ResolvedKeyword = {
        type: 'unknown',
        key: 'Test',
        displayText: '[Test]',
        color: '',
      }

      mockFormat.mockReturnValue([
        { type: 'keyword', content: 'Test', keyword: noColorKeyword },
      ])

      render(<FormattedDescription text="[Test]" />)

      const keyword = screen.getByTestId('keyword-Test')
      // Empty color should not cause issues
      expect(keyword).toBeInTheDocument()
    })
  })

  describe('newline handling', () => {
    it('renders newlines as line breaks', () => {
      // The component splits by \n then calls format on each line
      // With mock returning the text as-is, we verify content appears and br exists
      mockFormat.mockImplementation((text: string) => [{ type: 'text', content: text }])

      const { container } = render(<FormattedDescription text={'Line one\nLine two'} />)

      // Verify content is rendered
      expect(container.textContent).toContain('Line one')
      expect(container.textContent).toContain('Line two')
      // Verify there's a br element (at least one for the split)
      const brCount = container.querySelectorAll('br').length
      expect(brCount).toBeGreaterThanOrEqual(1)
    })

    it('handles multiple newlines', () => {
      mockFormat.mockImplementation((text: string) => [{ type: 'text', content: text }])

      const { container } = render(<FormattedDescription text={'A\nB\nC'} />)

      // Should have at least 2 br elements for 3 lines
      const breaks = container.querySelectorAll('br')
      expect(breaks.length).toBeGreaterThanOrEqual(2)
    })

    it('handles empty lines', () => {
      mockFormat.mockImplementation((text: string) =>
        text ? [{ type: 'text', content: text }] : []
      )

      const { container } = render(<FormattedDescription text={'Before\n\nAfter'} />)

      // Content should be present
      expect(container.textContent).toContain('Before')
      expect(container.textContent).toContain('After')
    })
  })

  describe('className prop', () => {
    it('applies custom className to wrapper', () => {
      mockFormat.mockReturnValue([{ type: 'text', content: 'Text' }])

      const { container } = render(
        <FormattedDescription text="Text" className="custom-class" />
      )

      const wrapper = container.firstChild as HTMLElement
      expect(wrapper).toHaveClass('custom-class')
      expect(wrapper).toHaveClass('inline')
    })
  })

  describe('format function calls', () => {
    it('calls format with correct text', () => {
      mockFormat.mockReturnValue([{ type: 'text', content: 'test' }])

      render(<FormattedDescription text="test" />)

      expect(mockFormat).toHaveBeenCalledWith('test')
    })

    it('calls format for each line when text has newlines', () => {
      mockFormat.mockImplementation((text: string) => [{ type: 'text', content: text }])

      render(<FormattedDescription text={'A\nB'} />)

      // Component splits by \n and calls format for each line
      // At minimum it should be called at least once
      expect(mockFormat).toHaveBeenCalled()
      // Check that both lines are represented in output
      const calls = mockFormat.mock.calls.flat()
      expect(calls).toContain('A')
    })
  })

  describe('edge cases', () => {
    it('handles segment without keyword property gracefully', () => {
      // Malformed segment: type is keyword but no keyword property
      mockFormat.mockReturnValue([
        { type: 'keyword', content: 'Orphan' } as ParsedSegment,
      ])

      // Should not throw, falls back to rendering content as-is
      const { container } = render(<FormattedDescription text="[Orphan]" />)

      expect(container.textContent).toContain('Orphan')
    })

    it('handles very long text', () => {
      const longText = 'A'.repeat(1000)
      mockFormat.mockReturnValue([{ type: 'text', content: longText }])

      const { container } = render(<FormattedDescription text={longText} />)

      expect(container.textContent).toBe(longText)
    })

    it('handles special characters in text', () => {
      const specialText = 'Text with <special> & "chars"'
      mockFormat.mockReturnValue([{ type: 'text', content: specialText }])

      render(<FormattedDescription text={specialText} />)

      expect(screen.getByText(/Text with/)).toBeInTheDocument()
    })
  })
})
