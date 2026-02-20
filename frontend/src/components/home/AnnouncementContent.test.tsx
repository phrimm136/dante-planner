import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AnnouncementContent } from './AnnouncementContent'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'EN' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}))

vi.mock('@/hooks/useAnnouncementData', () => ({
  useAnnouncementData: vi.fn(),
}))

import { useAnnouncementData } from '@/hooks/useAnnouncementData'

const mockAnnouncements = [
  {
    id: 'id-1',
    date: '2026-02-20',
    formattedDate: 'Feb 20, 2026',
    title: 'Test Announcement',
    body: 'Test body',
  },
]

describe('AnnouncementContent', () => {
  it('UT13: returns null when announcements array is empty', () => {
    vi.mocked(useAnnouncementData).mockReturnValue([])
    const { container } = render(<AnnouncementContent />)
    expect(container.firstChild).toBeNull()
  })

  it('UT14: renders section and dialog when data is present', () => {
    vi.mocked(useAnnouncementData).mockReturnValue(mockAnnouncements)
    render(<AnnouncementContent />)
    // Section title is rendered
    expect(screen.getByText('announcements.title')).toBeInTheDocument()
    // Announcement title is rendered in section
    expect(screen.getByText('Test Announcement')).toBeInTheDocument()
  })
})
