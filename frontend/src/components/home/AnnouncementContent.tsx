import { useState } from 'react'

import { useAnnouncementData } from '@/hooks/useAnnouncementData'
import { AnnouncementSection } from '@/components/home/AnnouncementSection'
import { AnnouncementDialog } from '@/components/home/AnnouncementDialog'

/**
 * Announcement orchestrator component.
 * Calls useAnnouncementData — must be wrapped in Suspense boundary.
 *
 * Returns null when no active announcements exist (empty list or all expired).
 */
export function AnnouncementContent() {
  const announcements = useAnnouncementData()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [initialSelectedId, setInitialSelectedId] = useState<string | null>(null)

  if (announcements.length === 0) {
    return null
  }

  const handleViewAll = () => {
    setInitialSelectedId(null)
    setDialogOpen(true)
  }

  const handleOpenAnnouncement = (id: string) => {
    setInitialSelectedId(id)
    setDialogOpen(true)
  }

  return (
    <>
      <AnnouncementSection
        announcements={announcements}
        onViewAll={handleViewAll}
        onOpenAnnouncement={handleOpenAnnouncement}
      />
      <AnnouncementDialog
        announcements={announcements}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        initialSelectedId={initialSelectedId}
      />
    </>
  )
}
