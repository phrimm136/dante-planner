import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ThemePackTrackerCard } from '../ThemePackTrackerCard'
import type { ThemePackEntry } from '@/pages/themePack'
import type { NoteContent } from '@/shared/noteEditor'

// Mock react-i18next with initReactI18next for proper module loading
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => {
        const translations: Record<string, string> = {
          'planner:common.markAsDone': 'Mark as Done',
          'planner:common.markAsNotDone': 'Mark as Not Done',
          'planner:common.viewNotes': 'View Notes',
          'common:markAsDone': 'Mark as Done',
          'common:markAsNotDone': 'Mark as Not Done',
          'common:viewNotes': 'View Notes',
        }
        return translations[key] ?? key
      },
    }),
  }
})

// Mock ThemePackViewer to simplify testing
vi.mock('../../floorTheme/ThemePackViewer', () => ({
  ThemePackViewer: ({
    packName,
    overlay,
    className,
    isSelected,
  }: {
    packName: string
    overlay?: React.ReactNode
    className?: string
    isSelected?: boolean
  }) => (
    <div
      data-testid="theme-pack-viewer"
      className={className ?? ''}
      data-selected={isSelected ?? false}
    >
      <img src="/mock.webp" alt={packName} />
      <span>{packName}</span>
      {overlay}
    </div>
  ),
}))

// Mock FloorNoteDialog
vi.mock('../FloorNoteDialog', () => ({
  FloorNoteDialog: ({ open }: { open: boolean }) =>
    open ? <div data-testid="floor-note-dialog">Notes Dialog</div> : null,
}))

describe('ThemePackTrackerCard', () => {
  const mockPackEntry: ThemePackEntry = {
    themePackConfig: {
      textColor: 'FFFFFF',
    },
    exceptionConditions: [],
    specificEgoGiftPool: [],
  }

  const mockNoteContent: NoteContent = {
    content: {
      type: 'doc',
      content: [],
    },
  }

  const defaultProps = {
    packId: 'pack1',
    packEntry: mockPackEntry,
    packName: 'Test Pack',
    floorNumber: 1,
    noteContent: mockNoteContent,
    isDone: false,
    onToggleDone: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Basic Rendering', () => {
    it('renders pack name via ThemePackViewer', () => {
      render(<ThemePackTrackerCard {...defaultProps} />)
      expect(screen.getByText('Test Pack')).toBeInTheDocument()
    })

    it('renders ThemePackViewer', () => {
      render(<ThemePackTrackerCard {...defaultProps} />)
      expect(screen.getByTestId('theme-pack-viewer')).toBeInTheDocument()
    })
  })

  describe('Hover Behavior', () => {
    it('calls onHoverChange when mouse enters and leaves', () => {
      const onHoverChange = vi.fn()

      render(<ThemePackTrackerCard {...defaultProps} onHoverChange={onHoverChange} />)

      const viewer = screen.getByTestId('theme-pack-viewer')
      const hoverTarget = viewer.parentElement!

      fireEvent.mouseEnter(hoverTarget)
      expect(onHoverChange).toHaveBeenCalledWith(true)

      fireEvent.mouseLeave(hoverTarget)
      expect(onHoverChange).toHaveBeenCalledWith(false)
    })
  })

  describe('Done State', () => {
    it('applies brightness class when isDone is true', () => {
      render(<ThemePackTrackerCard {...defaultProps} isDone={true} />)

      const viewer = screen.getByTestId('theme-pack-viewer')
      expect(viewer.className).toContain('brightness-50')
    })

    it('shows action buttons on hover', () => {
      render(<ThemePackTrackerCard {...defaultProps} isDone={true} />)

      const viewer = screen.getByTestId('theme-pack-viewer')
      const hoverTarget = viewer.parentElement!
      fireEvent.mouseEnter(hoverTarget)

      expect(hoverTarget.innerHTML).toContain('svg')
    })
  })

  describe('Focus Toggle', () => {
    it('calls onFocusToggle when card is clicked', () => {
      const onFocusToggle = vi.fn()

      render(<ThemePackTrackerCard {...defaultProps} onFocusToggle={onFocusToggle} />)

      fireEvent.click(screen.getByRole('button', { name: 'Test Pack' }))
      expect(onFocusToggle).toHaveBeenCalledOnce()
    })

    it('passes isFocused as isSelected to ThemePackViewer', () => {
      render(<ThemePackTrackerCard {...defaultProps} isFocused={true} />)

      const viewer = screen.getByTestId('theme-pack-viewer')
      expect(viewer.getAttribute('data-selected')).toBe('true')
    })

    it('does not call onFocusToggle when done button is clicked', () => {
      const onFocusToggle = vi.fn()

      render(<ThemePackTrackerCard {...defaultProps} onFocusToggle={onFocusToggle} />)

      const viewer = screen.getByTestId('theme-pack-viewer')
      const hoverTarget = viewer.parentElement!
      fireEvent.mouseEnter(hoverTarget)

      fireEvent.click(screen.getByRole('button', { name: 'Mark as Done' }))
      // the done button sits outside the card's click target, so it cannot toggle focus
      expect(onFocusToggle).not.toHaveBeenCalled()
    })
  })

  describe('Toggle Done Action', () => {
    it('calls onToggleDone when done button is clicked', () => {
      const onToggleDone = vi.fn()

      render(<ThemePackTrackerCard {...defaultProps} onToggleDone={onToggleDone} />)

      // Trigger hover to show action buttons
      const viewer = screen.getByTestId('theme-pack-viewer')
      const hoverTarget = viewer.parentElement!
      fireEvent.mouseEnter(hoverTarget)

      // Click the done button in the hover overlay
      fireEvent.click(screen.getByRole('button', { name: 'Mark as Done' }))
      expect(onToggleDone).toHaveBeenCalledOnce()
    })
  })
})
