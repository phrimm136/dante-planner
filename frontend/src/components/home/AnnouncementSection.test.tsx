import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AnnouncementSection, AnnouncementSkeleton } from './AnnouncementSection'
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
    title: 'Latest Update',
    body: 'Body text',
  },
  {
    id: 'id-2',
    date: '2026-02-19',
    formattedDate: 'Feb 19, 2026',
    title: 'Previous Update',
    body: 'Older body',
  },
]

describe('AnnouncementSection', () => {
  it('UT5: renders announcement title and formatted date', () => {
    render(
      <AnnouncementSection announcements={mockAnnouncements} onViewAll={vi.fn()} onOpenAnnouncement={vi.fn()} />
    )
    expect(screen.getByText('Latest Update')).toBeInTheDocument()
    expect(screen.getByText('Feb 20, 2026')).toBeInTheDocument()
  })

  it('UT6: calls onViewAll when "View All" button clicked', async () => {
    const onViewAll = vi.fn()
    const user = userEvent.setup()
    render(
      <AnnouncementSection announcements={mockAnnouncements} onViewAll={onViewAll} onOpenAnnouncement={vi.fn()} />
    )

    await user.click(screen.getByRole('button', { name: 'announcements.viewAll' }))
    expect(onViewAll).toHaveBeenCalledTimes(1)
  })

  it('clicking a title button calls onOpenAnnouncement with the correct id', async () => {
    const onViewAll = vi.fn()
    const onOpenAnnouncement = vi.fn()
    const user = userEvent.setup()

    render(
      <AnnouncementSection announcements={mockAnnouncements} onViewAll={onViewAll} onOpenAnnouncement={onOpenAnnouncement} />
    )

    await user.click(screen.getByRole('button', { name: 'Latest Update' }))
    expect(onViewAll).not.toHaveBeenCalled()
    expect(onOpenAnnouncement).toHaveBeenCalledWith('id-1')
  })

  it('renders section title from i18n', () => {
    render(
      <AnnouncementSection announcements={mockAnnouncements} onViewAll={vi.fn()} onOpenAnnouncement={vi.fn()} />
    )
    expect(screen.getByText('announcements.title')).toBeInTheDocument()
  })
})

describe('AnnouncementSkeleton', () => {
  it('UT7: renders without props', () => {
    const { container } = render(<AnnouncementSkeleton />)
    expect(container.firstChild).toBeTruthy()
  })
})
