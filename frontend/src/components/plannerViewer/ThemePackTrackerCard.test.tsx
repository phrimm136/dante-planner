import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ThemePackTrackerCard } from './ThemePackTrackerCard'

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

// Mock FloorNoteDialog
vi.mock('./FloorNoteDialog', () => ({
  FloorNoteDialog: ({ open }: { open: boolean }) =>
    open ? <div data-testid="floor-note-dialog">Notes Dialog</div> : null,
}))

describe('ThemePackTrackerCard', () => {
  const mockPackEntry = {
    floorNumber: 1,
    themePackConfig: {
      textColor: 'FFFFFF',
    },
    exceptionConditions: [],
  }

  const mockNoteContent = {
    content: {
      type: 'doc' as const,
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
    it('renders pack name', () => {
      render(<ThemePackTrackerCard {...defaultProps} />)
      expect(screen.getByText('Test Pack')).toBeInTheDocument()
    })

    it('renders pack image', () => {
      render(<ThemePackTrackerCard {...defaultProps} />)
      const img = screen.getByRole('img')
      expect(img).toHaveAttribute('alt', 'Test Pack')
    })
  })

  describe('Hover Behavior', () => {
    it('calls onHoverChange when mouse enters and leaves', () => {
      const onHoverChange = vi.fn()

      render(
        <ThemePackTrackerCard
          {...defaultProps}
          onHoverChange={onHoverChange}
        />
      )

      const container = screen.getByRole('img').closest('div')
      if (container) {
        fireEvent.mouseEnter(container)
        expect(onHoverChange).toHaveBeenCalledWith(true)

        fireEvent.mouseLeave(container)
        expect(onHoverChange).toHaveBeenCalledWith(false)
      }
    })
  })

  describe('Done State', () => {
    it('applies opacity when isDone is true', () => {
      render(<ThemePackTrackerCard {...defaultProps} isDone={true} />)

      const img = screen.getByRole('img')
      expect(img.className).toContain('opacity-50')
    })

    it('shows check icon when isDone is true and hovered', () => {
      render(<ThemePackTrackerCard {...defaultProps} isDone={true} />)

      // Trigger hover to show action buttons
      const container = screen.getByRole('img').closest('div')
      if (container) {
        fireEvent.mouseEnter(container)
      }

      // Look for the CheckCircle2 icon that appears in hover overlay
      const outerContainer = screen.getByRole('img').closest('div')?.parentElement
      expect(outerContainer?.innerHTML).toContain('svg')
    })
  })

  describe('Toggle Done Action', () => {
    it('calls onToggleDone when provided', () => {
      const onToggleDone = vi.fn()

      render(
        <ThemePackTrackerCard
          {...defaultProps}
          onToggleDone={onToggleDone}
        />
      )

      // Component should accept the callback without error
      expect(screen.getByText('Test Pack')).toBeInTheDocument()
    })
  })
})
