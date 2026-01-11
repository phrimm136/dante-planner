import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ThemePackTrackerCard } from './ThemePackTrackerCard'

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      if (key === 'common.markAsDone') return 'Mark as Done'
      if (key === 'common.markAsNotDone') return 'Mark as Not Done'
      if (key === 'common.viewNotes') return 'View Notes'
      return key
    },
  }),
}))

// Mock ThemePackViewer
vi.mock('@/components/floorTheme/ThemePackViewer', () => ({
  ThemePackViewer: ({
    packName,
    className,
  }: {
    packName: string
    className?: string
  }) => (
    <div data-testid="theme-pack-viewer" className={className}>
      {packName}
    </div>
  ),
}))

// Mock FloorNoteDialog
vi.mock('./FloorNoteDialog', () => ({
  FloorNoteDialog: ({ open }: { open: boolean }) =>
    open ? <div data-testid="floor-note-dialog">Notes Dialog</div> : null,
}))

// Mock UI components
vi.mock('@/components/ui/button', () => ({
  Button: ({
    children,
    onClick,
  }: {
    children: React.ReactNode
    onClick?: () => void
  }) => (
    <button onClick={onClick} data-testid="button">
      {children}
    </button>
  ),
}))

describe('ThemePackTrackerCard', () => {
  const mockPackEntry = {
    floorNumber: 1,
    themePackConfig: {
      textColor: 'FFFFFF',
    },
  }

  const mockNoteContent = {
    content: {
      type: 'doc' as const,
      content: [],
    },
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Hover Behavior', () => {
    it('shows action buttons on hover', () => {
      render(
        <ThemePackTrackerCard
          packId="pack1"
          packEntry={mockPackEntry}
          packName="Test Pack"
          floorNumber={1}
          noteContent={mockNoteContent}
          isDone={false}
          onToggleDone={vi.fn()}
        />
      )

      const container = screen.getByTestId('theme-pack-viewer').parentElement
      if (container) {
        fireEvent.mouseEnter(container)

        expect(screen.getByText('Mark as Done')).toBeDefined()
        expect(screen.getByText('View Notes')).toBeDefined()
      }
    })

    it('calls onHoverChange when mouse enters', () => {
      const onHoverChange = vi.fn()

      render(
        <ThemePackTrackerCard
          packId="pack1"
          packEntry={mockPackEntry}
          packName="Test Pack"
          floorNumber={1}
          noteContent={mockNoteContent}
          isDone={false}
          onToggleDone={vi.fn()}
          onHoverChange={onHoverChange}
        />
      )

      const container = screen.getByTestId('theme-pack-viewer').parentElement
      if (container) {
        fireEvent.mouseEnter(container)
        expect(onHoverChange).toHaveBeenCalledWith(true)
      }
    })

    it('calls onHoverChange when mouse leaves', () => {
      const onHoverChange = vi.fn()

      render(
        <ThemePackTrackerCard
          packId="pack1"
          packEntry={mockPackEntry}
          packName="Test Pack"
          floorNumber={1}
          noteContent={mockNoteContent}
          isDone={false}
          onToggleDone={vi.fn()}
          onHoverChange={onHoverChange}
        />
      )

      const container = screen.getByTestId('theme-pack-viewer').parentElement
      if (container) {
        fireEvent.mouseEnter(container)
        fireEvent.mouseLeave(container)
        expect(onHoverChange).toHaveBeenCalledWith(false)
      }
    })
  })

  describe('Mark as Done', () => {
    it('calls onToggleDone when clicked', () => {
      const onToggleDone = vi.fn()

      render(
        <ThemePackTrackerCard
          packId="pack1"
          packEntry={mockPackEntry}
          packName="Test Pack"
          floorNumber={1}
          noteContent={mockNoteContent}
          isDone={false}
          onToggleDone={onToggleDone}
        />
      )

      const container = screen.getByTestId('theme-pack-viewer').parentElement
      if (container) {
        fireEvent.mouseEnter(container)

        const markDoneButton = screen.getByText('Mark as Done')
        fireEvent.click(markDoneButton)

        expect(onToggleDone).toHaveBeenCalledTimes(1)
      }
    })

    it('shows "Mark as Not Done" when isDone is true', () => {
      render(
        <ThemePackTrackerCard
          packId="pack1"
          packEntry={mockPackEntry}
          packName="Test Pack"
          floorNumber={1}
          noteContent={mockNoteContent}
          isDone={true}
          onToggleDone={vi.fn()}
        />
      )

      const container = screen.getByTestId('theme-pack-viewer').parentElement
      if (container) {
        fireEvent.mouseEnter(container)
        expect(screen.getByText('Mark as Not Done')).toBeDefined()
      }
    })

    it('applies opacity-50 when isDone is true', () => {
      render(
        <ThemePackTrackerCard
          packId="pack1"
          packEntry={mockPackEntry}
          packName="Test Pack"
          floorNumber={1}
          noteContent={mockNoteContent}
          isDone={true}
          onToggleDone={vi.fn()}
        />
      )

      const viewer = screen.getByTestId('theme-pack-viewer')
      expect(viewer.className).toContain('opacity-50')
    })
  })

  describe('View Notes', () => {
    it('opens notes dialog when View Notes clicked', () => {
      render(
        <ThemePackTrackerCard
          packId="pack1"
          packEntry={mockPackEntry}
          packName="Test Pack"
          floorNumber={1}
          noteContent={mockNoteContent}
          isDone={false}
          onToggleDone={vi.fn()}
        />
      )

      const container = screen.getByTestId('theme-pack-viewer').parentElement
      if (container) {
        fireEvent.mouseEnter(container)

        const viewNotesButton = screen.getByText('View Notes')
        fireEvent.click(viewNotesButton)

        expect(screen.getByTestId('floor-note-dialog')).toBeDefined()
      }
    })
  })
})
