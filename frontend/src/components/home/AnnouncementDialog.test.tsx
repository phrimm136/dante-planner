import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AnnouncementDialog } from './AnnouncementDialog'
import type { Announcement } from '@/types/AnnouncementTypes'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'EN' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}))

const mockAnnouncements: Announcement[] = [
  {
    id: 'id-1',
    date: '2026-02-20',
    formattedDate: 'Feb 20, 2026',
    title: 'First Announcement',
    body: 'First body text\nWith newlines',
  },
  {
    id: 'id-2',
    date: '2026-02-19',
    formattedDate: 'Feb 19, 2026',
    title: 'Second Announcement',
    body: 'Second body text',
  },
]

describe('AnnouncementDialog', () => {
  it('UT8: opens in list view showing all announcements', () => {
    render(
      <AnnouncementDialog
        announcements={mockAnnouncements}
        open={true}
        onOpenChange={vi.fn()}
      />
    )
    expect(screen.getByText('First Announcement')).toBeInTheDocument()
    expect(screen.getByText('Second Announcement')).toBeInTheDocument()
    // Body should NOT be visible in list view
    expect(screen.queryByText((_, el) => el?.textContent === 'First body text\nWith newlines')).not.toBeInTheDocument()
  })

  it('UT9: clicking a row transitions to detail view', async () => {
    const user = userEvent.setup()
    render(
      <AnnouncementDialog
        announcements={mockAnnouncements}
        open={true}
        onOpenChange={vi.fn()}
      />
    )

    await user.click(screen.getByText('First Announcement'))

    // Detail view: body is visible (use function matcher — RTL normalizes whitespace in strings)
    expect(screen.getByText((_, el) => el?.textContent === 'First body text\nWith newlines')).toBeInTheDocument()
    // List items no longer visible
    expect(screen.queryByText('Second Announcement')).not.toBeInTheDocument()
  })

  it('UT10: Back button returns to list view without closing dialog', async () => {
    const onOpenChange = vi.fn()
    const user = userEvent.setup()
    render(
      <AnnouncementDialog
        announcements={mockAnnouncements}
        open={true}
        onOpenChange={onOpenChange}
      />
    )

    // Navigate to detail view
    await user.click(screen.getByText('First Announcement'))
    expect(screen.getByText((_, el) => el?.textContent === 'First body text\nWith newlines')).toBeInTheDocument()

    // Click Back
    await user.click(screen.getByRole('button', { name: /announcements\.backToList/i }))

    // Back in list view; dialog still open
    expect(screen.getByText('First Announcement')).toBeInTheDocument()
    expect(screen.getByText('Second Announcement')).toBeInTheDocument()
    expect(onOpenChange).not.toHaveBeenCalled()
  })

  it('UT11: close and reopen resets to list view', async () => {
    const user = userEvent.setup()
    const { rerender } = render(
      <AnnouncementDialog
        announcements={mockAnnouncements}
        open={true}
        onOpenChange={vi.fn()}
      />
    )

    // Navigate to detail
    await user.click(screen.getByText('First Announcement'))
    expect(screen.getByText((_, el) => el?.textContent === 'First body text\nWith newlines')).toBeInTheDocument()

    // Close dialog (open=false)
    rerender(
      <AnnouncementDialog
        announcements={mockAnnouncements}
        open={false}
        onOpenChange={vi.fn()}
      />
    )

    // Reopen dialog (open=true)
    rerender(
      <AnnouncementDialog
        announcements={mockAnnouncements}
        open={true}
        onOpenChange={vi.fn()}
      />
    )

    // Should be back in list view
    expect(screen.getByText('First Announcement')).toBeInTheDocument()
    expect(screen.queryByText((_, el) => el?.textContent === 'First body text\nWith newlines')).not.toBeInTheDocument()
  })

  it('UT12: empty announcements list shows noAnnouncements string', () => {
    render(
      <AnnouncementDialog
        announcements={[]}
        open={true}
        onOpenChange={vi.fn()}
      />
    )
    expect(screen.getByText('announcements.noAnnouncements')).toBeInTheDocument()
  })

  it('mid-open initialSelectedId change navigates to new detail view', async () => {
    const { rerender } = render(
      <AnnouncementDialog
        announcements={mockAnnouncements}
        open={true}
        onOpenChange={vi.fn()}
        initialSelectedId={null}
      />
    )

    // Dialog is open in list view
    expect(screen.getByText('First Announcement')).toBeInTheDocument()

    // Parent changes the target id while dialog stays open
    rerender(
      <AnnouncementDialog
        announcements={mockAnnouncements}
        open={true}
        onOpenChange={vi.fn()}
        initialSelectedId="id-1"
      />
    )

    // Dialog should now show detail view for id-1
    expect(screen.getByText((_, el) => el?.textContent === 'First body text\nWith newlines')).toBeInTheDocument()
    expect(screen.queryByText('Second Announcement')).not.toBeInTheDocument()
  })
})
